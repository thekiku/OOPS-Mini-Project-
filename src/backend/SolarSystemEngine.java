// Swing UI
import javax.swing.*;
// Image IO
import javax.imageio.ImageIO;
// AWT drawing, events, and geometry
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
// File and resource loading
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
// Collections and concurrency
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
// Screen sizing helpers
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

// Main window that hosts the solar system simulation UI and physics loop.
public class SolarSystemEngine extends JFrame {

    // ── constants ─────────────────────────────────────────────────────────────
    // Window sizing + core physics parameters.
    static final int W = 1180, H = 760;
    static final int CTRL_W = 380;
    static final int CTRL_RIGHT_GUTTER = 0;
    static final double G = 0.5;
    static final int MAX_TRAIL = 150;

    // ── ui palette ────────────────────────────────────────────────────────────
    // Theme colors and fonts for the command panel + chat UI.
    static final Color UI_BG        = new Color(7, 11, 26);
    static final Color UI_CARD_T    = new Color(18, 30, 60, 230);
    static final Color UI_CARD_B    = new Color(10, 18, 42, 230);
    static final Color UI_CARD_EDGE = new Color(80, 120, 200, 90);
    static final Color UI_ACCENT    = new Color(100, 180, 255);
    static final Color UI_TEXT      = new Color(225, 234, 252);
    static final Color UI_MUTED     = new Color(155, 175, 210);
    static final Color UI_GOLD      = new Color(255, 215, 140);
    static final Font  FN           = new Font("Segoe UI", Font.PLAIN,  13);
    static final Font  FN_B         = new Font("Segoe UI", Font.BOLD,   13);
    static final Font  FN_T         = new Font("Segoe UI", Font.BOLD,   15);
    static final Font  FN_H         = new Font("Segoe UI", Font.BOLD,   20);
    static final Font  FN_S         = new Font("Segoe UI", Font.PLAIN,  12);

    // ── simulation palette ────────────────────────────────────────────────────
    // Planet color triplets used for shading/lighting.
    static final Color[][] PAL = {
        {new Color(85,147,219),  new Color(37,110,200),  new Color(12,60,130)},
        {new Color(220,110,80),  new Color(200,70,40),   new Color(130,40,15)},
        {new Color(80,200,160),  new Color(29,158,117),  new Color(8,80,55)},
        {new Color(240,195,80),  new Color(220,150,30),  new Color(140,90,10)},
        {new Color(220,120,170), new Color(200,80,130),  new Color(110,30,70)},
        {new Color(170,168,160), new Color(130,128,122), new Color(70,68,65)},
        {new Color(120,200,80),  new Color(90,160,40),   new Color(50,100,15)},
        {new Color(160,80,220),  new Color(120,40,180),  new Color(70,15,120)},
    };

    // ── body types ────────────────────────────────────────────────────────────
    // All entity categories supported in the simulation.
    enum BT { PLANET, MOON, SHUTTLE, BLACK_HOLE, PULSAR, SUN, WORMHOLE, ASTEROID }

    // ── body ──────────────────────────────────────────────────────────────────
    // Core physics entity: position, velocity, mass, orbit parameters, visuals.
    static class Body {
        BT type;
        double x, y, vx, vy, mass, radius;
        double orbitDist, orbitAngle, orbitEcc, orbitSpeed;
        boolean kepler = false;
        double shuttleAngle = 0;
        boolean thrustMain, thrustLeft, thrustRight, thrustReverse;
        int ci;
        boolean rings; double ringTilt;
        int moons; double[] mAngle, mSpeed, mDist;
        List<Point2D.Double> trail = new ArrayList<>();
        boolean dead = false; String name = "";
        double bhRot = 0;
        double pulsarPhase = Math.random()*Math.PI*2, pulsarPeriod = 0.8+Math.random()*1.5;
        Body whPartner = null; double whPulse = 0;
        boolean spag = false; double spagP = 0, spagRaw = 0, spagA = 0; Body spagTarget = null;

        Body(double x,double y,double mass,double radius,BT type,int ci){
            this.x=x;this.y=y;this.mass=mass;this.radius=radius;this.type=type;this.ci=ci;
            rings=(type==BT.PLANET)&&radius>12&&Math.random()<0.35; ringTilt=0.2+Math.random()*0.35;
            moons=(type==BT.PLANET&&radius>9)?(int)(Math.random()*3):0;
            mAngle=new double[moons];mSpeed=new double[moons];mDist=new double[moons];
            for(int i=0;i<moons;i++){
                mAngle[i]=Math.random()*Math.PI*2;
                mSpeed[i]=(Math.random()*0.04+0.02)*(Math.random()<0.5?1:-1);
                mDist[i]=radius*1.9+Math.random()*radius;
            }
        }
        void setMoons(int n){
            moons=Math.max(0,n); mAngle=new double[moons];mSpeed=new double[moons];mDist=new double[moons];
            for(int i=0;i<moons;i++){mAngle[i]=Math.random()*Math.PI*2;mSpeed[i]=(Math.random()*0.04+0.02)*(Math.random()<0.5?1:-1);mDist[i]=radius*1.9+Math.random()*radius;}
        }
        // FIX #1: correct orbital speed formula — sqrt(G*M/r), multiplier 0.018 (not 0.012)
        void orbit(double cx,double cy,double sm){
            double dx=x-cx,dy=y-cy;
            orbitDist=Math.sqrt(dx*dx+dy*dy);
            orbitAngle=Math.atan2(dy,dx);
            orbitSpeed=Math.sqrt(G*sm/Math.max(orbitDist,1))*0.018;
            kepler=true;
        }
        Point2D.Double kpos(double cx,double cy){
            double e=Math.min(orbitEcc,0.85);
            double r=orbitDist*(1-e*e)/(1+e*Math.cos(orbitAngle));
            return new Point2D.Double(cx+r*Math.cos(orbitAngle),cy+r*Math.sin(orbitAngle));
        }
    }

    // ── particle ──────────────────────────────────────────────────────────────
    // Small visual particle for explosions and dust effects.
    static class Particle{
        double x,y,vx,vy; float sz; Color c; int life,maxLife;
        Particle(double x,double y,Color c){this(x,y,c,(Math.random()-.5)*6,(Math.random()-.5)*6,1.5f);}
        Particle(double x,double y,Color c,double vx,double vy,float sz){
            this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.c=c;this.sz=sz;
            maxLife=25+(int)(Math.random()*38);life=maxLife;
        }
        void tick(double dt){x+=vx*dt;y+=vy*dt;vx*=0.93;vy*=0.93;life--;}
        boolean dead(){return life<=0;}
        void draw(Graphics2D g){
            float a=(float)life/maxLife;
            int alpha=Math.max(0,Math.min(255,(int)(a*195)));
            g.setColor(new Color(c.getRed(),c.getGreen(),c.getBlue(),alpha));
            int s=Math.max(1,(int)(sz*a*3));
            g.fillOval((int)(x-s*.5),(int)(y-s*.5),s,s);
        }
    }

    // ── state ─────────────────────────────────────────────────────────────────
    // Top-level UI controls and the simulation panel instance.
    SimPanel sim;
    JLabel statusLabel;
    JSlider sunMassSlider,sunRadSlider,speedSlider,pSizeSlider,eccSlider;
    JComboBox<String> bodyTypeCombo,colorCombo,themeCombo;

    // ── entry ─────────────────────────────────────────────────────────────────
    public static void main(String[] args){ SwingUtilities.invokeLater(SolarSystemEngine::new); }

    Runnable onBack = null;

    public SolarSystemEngine(){
        this(null);
    }

    public SolarSystemEngine(Runnable backCallback){
        this.onBack = backCallback;
        setTitle("Solar System Physics Engine");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(UI_BG);
        // FIX #3: build sliders BEFORE SimPanel, then load after layout
        buildSliders();
        sim=new SimPanel();
        // fit to screen — never exceed usable screen height
        GraphicsEnvironment ge=GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle screen=ge.getMaximumWindowBounds();
        int winW=Math.min(W,screen.width);
        int winH=screen.height;
        add(sim,BorderLayout.CENTER);
        add(buildControlPanel(),BorderLayout.EAST);   // orbital controls on the right
        add(buildChatPanel(),BorderLayout.SOUTH);     // chatbot spans the full bottom edge
        setSize(winW,winH);
        setLocationRelativeTo(null);
        setVisible(true);
        SwingUtilities.invokeLater(sim::loadSolarSystem);
        new Timer(500,e->updateStatus()).start();
    }

    void buildSliders(){
        sunMassSlider=mkSlider(200,8000,2000);
        sunRadSlider =mkSlider(8,80,28);
        speedSlider  =mkSlider(1,40,8);
        pSizeSlider  =mkSlider(3,30,8);
        eccSlider    =mkSlider(0,85,0);
        sunRadSlider.addChangeListener(e->{
            int r=sunRadSlider.getValue();
            sunMassSlider.setValue(Math.max(200,Math.min(8000,(int)(r*r*r*0.09))));
            if(sim!=null) sim.recompute();
        });
        sunMassSlider.addChangeListener(e->{ if(sim!=null) sim.recompute(); });
        bodyTypeCombo=new JComboBox<>(new String[]{"Planet","Moon","Space Shuttle","Black Hole","Pulsar","Wormhole","Asteroid Belt"});
        colorCombo   =new JComboBox<>(new String[]{"Blue","Coral","Teal","Amber","Pink","Gray","Green","Purple"});
        themeCombo   =new JComboBox<>(new String[]{"Default","Hail Mary","Interstellar"});
    }

    JSlider mkSlider(int mn,int mx,int v){
        JSlider s=new JSlider(mn,mx,v);
        s.setBackground(UI_CARD_T); s.setForeground(UI_ACCENT);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE,24)); return s;
    }

    JPanel buildControlPanel(){
        // scrollable inner panel
        JPanel p=new JPanel(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0,0,new Color(8,13,28),0,getHeight(),new Color(10,20,44)));
                g2.fillRect(0,0,getWidth(),getHeight());
                // subtle nebula blobs
                g2.setColor(new Color(60,100,200,18)); g2.fillOval(-80,-50,getWidth()+160,160);
                g2.setColor(new Color(40,80,170,14)); g2.fillOval(-50,getHeight()-130,getWidth()+100,200);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));

        // hero header
        JLabel hero=new JLabel("ORBITAL COMMAND",SwingConstants.CENTER);
        hero.setFont(FN_H); hero.setForeground(new Color(228,240,255)); hero.setAlignmentX(CENTER_ALIGNMENT);
        JLabel sub=new JLabel("Solar System Physics Engine",SwingConstants.CENTER);
        sub.setFont(FN_S); sub.setForeground(new Color(160,190,230)); sub.setAlignmentX(CENTER_ALIGNMENT);
        p.add(hero); p.add(javax.swing.Box.createVerticalStrut(2)); p.add(sub); p.add(javax.swing.Box.createVerticalStrut(8));

        // SUN card
        JPanel sunCard=card("☀  SUN");
        sunCard.add(srow("Mass",sunMassSlider));
        sunCard.add(gap(3));
        sunCard.add(srow("Radius",sunRadSlider));
        p.add(sunCard); p.add(gap(6));

        // SPAWN card
        JPanel spawnCard=card("✦  SPAWN BODY");
        styleCombo(bodyTypeCombo); styleCombo(colorCombo);
        spawnCard.add(crow("Type",bodyTypeCombo));  spawnCard.add(gap(3));
        spawnCard.add(crow("Color",colorCombo));    spawnCard.add(gap(3));
        spawnCard.add(srow("Size",pSizeSlider));    spawnCard.add(gap(3));
        spawnCard.add(srow("Ecc×100",eccSlider));   spawnCard.add(gap(4));
        spawnCard.add(btn("Load Solar System",  e->sim.loadSolarSystem()));         spawnCard.add(gap(2));
        spawnCard.add(btn("Add Random Orbiter", e->sim.addOrbiter()));              spawnCard.add(gap(2));
        spawnCard.add(btn("Scatter Asteroids",  e->sim.scatterAsteroids()));        spawnCard.add(gap(2));
        spawnCard.add(btn("Big Bang",           e->sim.bigBang()));                 spawnCard.add(gap(2));
        spawnCard.add(btn("Trigger Supernova",  e->sim.triggerSupernova(),new Color(112,28,20))); spawnCard.add(gap(2));
        spawnCard.add(btn("Clear All",          e->sim.clearAll(),new Color(80,22,22)));
        p.add(spawnCard); p.add(gap(6));

        // DISPLAY card
        JPanel dispCard=card("⊙  DISPLAY");
        dispCard.add(tog("Orbit Paths", true,  v->sim.showOrbits=v));  dispCard.add(gap(2));
        dispCard.add(tog("Body Trails", true,  v->sim.showTrails=v));  dispCard.add(gap(2));
        dispCard.add(tog("Pause Sim",   false, v->sim.paused=v));      dispCard.add(gap(3));
        styleCombo(themeCombo);
        themeCombo.addActionListener(e->{ if(sim!=null) sim.theme=themeCombo.getSelectedIndex(); });
        dispCard.add(crow("Theme",themeCombo));
        p.add(dispCard); p.add(gap(6));

        // SIMULATION card
        JPanel simCard=card("⚙  SIMULATION");
        simCard.add(srow("Speed×10",speedSlider));
        p.add(simCard); p.add(javax.swing.Box.createVerticalGlue());

        // STATUS card
        JPanel stCard=card("◈  STATUS");
        statusLabel=new JLabel("Bodies: 0",SwingConstants.CENTER);
        statusLabel.setForeground(new Color(190,212,248)); statusLabel.setFont(FN_B);
        statusLabel.setAlignmentX(CENTER_ALIGNMENT); stCard.add(statusLabel);
        p.add(gap(6)); p.add(stCard);

        // WASD hint
        JLabel wasd=new JLabel("<html><center><span style='color:#6ab4f5'>WASD</span> — control Space Shuttle<br><span style='color:#8cb8d8'>Click planet for close-up view</span></center></html>",SwingConstants.CENTER);
        wasd.setFont(FN_S); wasd.setAlignmentX(CENTER_ALIGNMENT);
        wasd.setBorder(BorderFactory.createEmptyBorder(4,0,0,0));
        p.add(wasd);

        // Back to Home button
        if(onBack != null){
            p.add(gap(6));
            JButton backBtn = btn("← Back to Home", e -> {
                setVisible(false);
                dispose();
                SwingUtilities.invokeLater(onBack);
            }, new Color(14, 28, 58));
            backBtn.setForeground(new Color(160, 200, 255));
            p.add(backBtn);
        }

        JPanel controlBody = new JPanel(new BorderLayout());
        controlBody.setOpaque(false);
        controlBody.add(p, BorderLayout.CENTER);

        JScrollPane scroll=new JScrollPane(controlBody);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scroll.setWheelScrollingEnabled(false);
        scroll.getViewport().setOpaque(false); scroll.setOpaque(false);

        JPanel shell=new JPanel(new BorderLayout());
        shell.setBackground(UI_BG);
        shell.setPreferredSize(new Dimension(CTRL_W,0)); // height auto from window
        shell.setMinimumSize(new Dimension(CTRL_W,0));
        shell.setBorder(BorderFactory.createMatteBorder(0,1,0,0,new Color(55,80,140)));
        shell.add(scroll,BorderLayout.CENTER);

        // Keep a right gutter so the command panel sits further left and stays fully visible.
        JPanel dock = new JPanel(new BorderLayout());
        dock.setBackground(UI_BG);
        dock.setOpaque(true);
        dock.add(shell, BorderLayout.WEST);
        dock.add(javax.swing.Box.createHorizontalStrut(CTRL_RIGHT_GUTTER), BorderLayout.EAST);
        dock.setPreferredSize(new Dimension(CTRL_W + CTRL_RIGHT_GUTTER, 0));
        dock.setMinimumSize(new Dimension(CTRL_W + CTRL_RIGHT_GUTTER, 0));
        return dock;
    }

    // ── AI chat panel ────────────────────────────────────────────────────────
    JTextArea chatDisplay;
    JTextField chatInput;
    boolean chatCollapsed = true;
    JPanel chatPanelRef;

    JPanel buildChatPanel(){
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(new Color(5,8,20));
        outer.setBorder(BorderFactory.createMatteBorder(1,0,0,0,new Color(40,65,130)));
        chatPanelRef = outer;

        // header bar
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(9,15,35));
        header.setBorder(BorderFactory.createEmptyBorder(6,14,6,14));
        JLabel title = new JLabel("  AI ASSISTANT  —  Ask anything about this simulation");
        title.setFont(new Font("Segoe UI",Font.BOLD,12));
        title.setForeground(new Color(130,190,255));
        JButton toggle = new JButton("▲ Expand");
        toggle.setFont(new Font("Segoe UI",Font.BOLD,11));
        toggle.setForeground(new Color(100,160,220));
        toggle.setBackground(new Color(14,24,52));
        toggle.setBorder(BorderFactory.createEmptyBorder(3,10,3,10));
        toggle.setFocusPainted(false);
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.add(title,BorderLayout.WEST);
        header.add(toggle,BorderLayout.EAST);

        // chat display
        chatDisplay = new JTextArea();
        chatDisplay.setEditable(false);
        chatDisplay.setLineWrap(true);
        chatDisplay.setWrapStyleWord(true);
        chatDisplay.setBackground(new Color(7,11,25));
        chatDisplay.setForeground(new Color(210,225,250));
        chatDisplay.setFont(new Font("Segoe UI",Font.PLAIN,13));
        chatDisplay.setBorder(BorderFactory.createEmptyBorder(10,14,10,14));
        chatDisplay.setText("Assistant: Hello! I'm here to help you understand this Solar System Physics Engine.\n"
            + "Ask me about: orbital mechanics, black holes, spaghettification, wormholes, the Space Shuttle controls,\n"
            + "planet close-ups, collision physics, or anything else you see in the simulation!\n");

        JScrollPane chatScroll = new JScrollPane(chatDisplay);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());
        chatScroll.setBackground(new Color(7,11,25));
        chatScroll.setPreferredSize(new Dimension(0,88));
        chatScroll.getVerticalScrollBar().setBackground(new Color(12,20,44));

        // input row
        JPanel inputRow = new JPanel(new BorderLayout(8,0));
        inputRow.setBackground(new Color(9,15,35));
        inputRow.setBorder(BorderFactory.createEmptyBorder(8,12,8,12));

        chatInput = new JTextField();
        chatInput.setBackground(new Color(14,22,48));
        chatInput.setForeground(new Color(220,232,255));
        chatInput.setCaretColor(new Color(100,180,255));
        chatInput.setFont(new Font("Segoe UI",Font.PLAIN,13));
        chatInput.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(45,70,130),1),
            BorderFactory.createEmptyBorder(6,10,6,10)));

        JButton sendBtn = new JButton("Send ↵");
        sendBtn.setFont(new Font("Segoe UI",Font.BOLD,12));
        sendBtn.setBackground(new Color(30,60,120));
        sendBtn.setForeground(new Color(180,215,255));
        sendBtn.setBorder(BorderFactory.createEmptyBorder(7,16,7,16));
        sendBtn.setFocusPainted(false);
        sendBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        inputRow.add(chatInput,BorderLayout.CENTER);
        inputRow.add(sendBtn,BorderLayout.EAST);

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(new Color(7,11,25));
        body.add(chatScroll,BorderLayout.CENTER);
        body.add(inputRow,BorderLayout.SOUTH);
        body.setVisible(!chatCollapsed);

        outer.add(header,BorderLayout.NORTH);
        outer.add(body,BorderLayout.CENTER);

        // send logic — async real AI via NVIDIA API
        Runnable sendAction = () -> {
            String q = chatInput.getText().trim();
            if(q.isEmpty()) return;
            chatDisplay.append("\nYou: " + q + "\n");
            chatDisplay.append("Assistant: thinking...\n");
            chatDisplay.setCaretPosition(chatDisplay.getDocument().getLength());
            chatInput.setText("");
            sendBtn.setEnabled(false);
            String question = q;
            new Thread(() -> {
                String reply = callNvidiaAPI(question);
                SwingUtilities.invokeLater(() -> {
                    // replace the "thinking..." line with the real answer
                    String txt = chatDisplay.getText();
                    int idx = txt.lastIndexOf("Assistant: thinking...");
                    if(idx >= 0){
                        try {
                            chatDisplay.getDocument().remove(idx, "Assistant: thinking...".length());
                            chatDisplay.getDocument().insertString(idx, "Assistant: " + reply, null);
                        } catch(Exception ex){
                            chatDisplay.append("Assistant: " + reply + "\n");
                        }
                    } else {
                        chatDisplay.append("Assistant: " + reply + "\n");
                    }
                    chatDisplay.setCaretPosition(chatDisplay.getDocument().getLength());
                    sendBtn.setEnabled(true);
                });
            }, "nvidia-chat").start();
        };
        sendBtn.addActionListener(e -> sendAction.run());
        chatInput.addActionListener(e -> sendAction.run());

        // collapse toggle
        toggle.addActionListener(e -> {
            chatCollapsed = !chatCollapsed;
            body.setVisible(!chatCollapsed);
            toggle.setText(chatCollapsed ? "▲ Expand" : "▼ Collapse");
            outer.revalidate();
            outer.repaint();
        });

        return outer;
    }

    // ── real NVIDIA API call ────────────────────────────────────────────────
    static final String NVIDIA_API_URL = "https://integrate.api.nvidia.com/v1/chat/completions";
    static final String NVIDIA_MODEL_DEFAULT = "meta/llama-3.1-8b-instruct";
    // Optional local placeholder: paste your NVIDIA key here, or set NVIDIA_API_KEY env var.
    static final String NVIDIA_API_KEY = "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";

    static final String SYSTEM_PROMPT =
        "You are an expert assistant embedded inside a Java Swing Solar System Physics Engine simulation. " +
        "Answer questions about the simulation, its features, and the underlying physics concisely and helpfully. " +
        "The simulation includes: Kepler orbital mechanics, n-body gravity, black holes with spaghettification, " +
        "pulsars, wormhole teleportation, asteroid belts, a flyable Space Shuttle (WASD controls), " +
        "planet close-up views with telemetry (mass, gravity, temperature, atmosphere, water, survival %), " +
        "collision physics with momentum conservation, 3 visual themes (Default/Hail Mary/Interstellar), " +
        "sun mass/radius sliders, orbit trails, a Big Bang mode, and a triggerable supernova event. " +
        "Keep answers under 3 sentences. Be specific and technical when asked. " +
        "If a question is unrelated to physics or this simulation, gently redirect.";

    String callNvidiaAPI(String userMessage) {
        try {
            String apiKey = System.getenv("NVIDIA_API_KEY");
            if ((apiKey == null || apiKey.isBlank()) && !NVIDIA_API_KEY.isBlank()) {
                apiKey = NVIDIA_API_KEY;
            }
            if (apiKey == null || apiKey.isBlank()) {
                return "Assistant is offline right now. You can still explore the simulation using the controls and sliders.";
            }

            String model = System.getenv("NVIDIA_MODEL");
            if (model == null || model.isBlank()) {
                model = NVIDIA_MODEL_DEFAULT;
            }

            java.net.URI uri = java.net.URI.create(NVIDIA_API_URL);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());

            String body = "{"
                + "\"model\":" + jsonString(model) + ","
                + "\"temperature\":0.4,"
                + "\"max_tokens\":300,"
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":" + jsonString(SYSTEM_PROMPT) + "},"
                + "{\"role\":\"user\",\"content\":" + jsonString(userMessage) + "}"
                + "]"
                + "}";

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
            }

            int status = conn.getResponseCode();
            java.io.InputStream is = (status < 400) ? conn.getInputStream() : conn.getErrorStream();
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            String response = sb.toString();

            if (status >= 400) {
                if (status == 401 || status == 403) return "Assistant is offline right now. You can still explore the simulation using the controls and sliders.";
                if (status == 429) return "Rate limit hit. Please wait a moment before asking again.";
                return "API error " + status + ". Check your NVIDIA_API_KEY.";
            }

            String text = extractNvidiaAssistantText(response);
            if (text == null || text.isBlank()) return "Could not parse response.";
            return text;

        } catch (java.net.SocketTimeoutException e) {
            return "Request timed out. Check your internet connection.";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    static String extractNvidiaAssistantText(String response) {
        // NVIDIA chat completions are OpenAI-compatible: choices[0].message.content
        int msgIdx = response.indexOf("\"message\"");
        int startSearch = msgIdx >= 0 ? msgIdx : 0;
        int contentIdx = response.indexOf("\"content\":", startSearch);
        if (contentIdx < 0) {
            return null;
        }
        int valueStart = contentIdx + "\"content\":".length();
        return parseJsonString(response, valueStart);
    }

    static String parseJsonString(String src, int startIndex) {
        int i = startIndex;
        while (i < src.length() && Character.isWhitespace(src.charAt(i))) i++;
        if (i >= src.length() || src.charAt(i) != '"') return null;
        i++; // opening quote

        StringBuilder out = new StringBuilder();
        boolean esc = false;
        while (i < src.length()) {
            char c = src.charAt(i++);
            if (esc) {
                switch (c) {
                    case 'n': out.append('\n'); break;
                    case 't': out.append('\t'); break;
                    case 'r': out.append('\r'); break;
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/': out.append('/'); break;
                    default: out.append(c); break;
                }
                esc = false;
            } else if (c == '\\') {
                esc = true;
            } else if (c == '"') {
                return out.toString();
            } else {
                out.append(c);
            }
        }
        return null;
    }

    static String jsonString(String s) {
        return "\"" + s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            + "\"";
    }

    void updateStatus(){
        if(sim==null) return;
        long bh=sim.bodies.stream().filter(b->b.type==BT.BLACK_HOLE).count();
        long wh=sim.bodies.stream().filter(b->b.type==BT.WORMHOLE).count();
        long sh=sim.bodies.stream().filter(b->b.type==BT.SHUTTLE).count();
        statusLabel.setText("<html><div style='text-align:center'>Bodies <b>"+sim.bodies.size()
            +"</b><br>BH "+bh+" | WH "+wh+" | Shuttle "+sh+"</div></html>");
    }

    // ── ui helpers ────────────────────────────────────────────────────────────
    JPanel card(String title){
        JPanel c=new JPanel(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                RoundRectangle2D box=new RoundRectangle2D.Double(.5,.5,getWidth()-1,getHeight()-1,16,16);
                g2.setPaint(new GradientPaint(0,0,UI_CARD_T,0,getHeight(),UI_CARD_B)); g2.fill(box);
                g2.setColor(UI_CARD_EDGE); g2.draw(box);
                g2.dispose();
            }
        };
        c.setOpaque(false); c.setLayout(new BoxLayout(c,BoxLayout.Y_AXIS));
        c.setAlignmentX(CENTER_ALIGNMENT); c.setMaximumSize(new Dimension(Integer.MAX_VALUE,2000));
        c.setBorder(BorderFactory.createEmptyBorder(6,8,7,8));
        JLabel lbl=new JLabel(title,SwingConstants.CENTER);
        lbl.setForeground(new Color(155,200,255)); lbl.setFont(FN_T); lbl.setAlignmentX(CENTER_ALIGNMENT);
        lbl.setBorder(BorderFactory.createEmptyBorder(0,0,4,0)); c.add(lbl);
        return c;
    }
    Component gap(int h){ return javax.swing.Box.createVerticalStrut(h); }
    JPanel srow(String lbl,JSlider s){
        JPanel r=new JPanel(new BorderLayout(8,0)); r.setBackground(new Color(16,26,50,200));
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE,36));
        JLabel l=new JLabel(lbl); l.setForeground(UI_MUTED); l.setFont(FN); l.setPreferredSize(new Dimension(78,20));
        JLabel v=new JLabel(String.valueOf(s.getValue()));
        v.setForeground(UI_GOLD); v.setFont(FN_B);
        v.setPreferredSize(new Dimension(58,22));
        v.setMinimumSize(new Dimension(58,22));
        v.setHorizontalAlignment(SwingConstants.CENTER);
        v.setOpaque(true);
        v.setBackground(new Color(24,38,74));
        v.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(65,95,155),1),
            BorderFactory.createEmptyBorder(1,6,1,6)));
        s.addChangeListener(e->v.setText(String.valueOf(s.getValue())));
        r.add(l,BorderLayout.WEST); r.add(s,BorderLayout.CENTER); r.add(v,BorderLayout.EAST);
        r.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(42,62,102),1),BorderFactory.createEmptyBorder(2,6,2,6)));
        return r;
    }
    JPanel crow(String lbl,JComponent c){
        JPanel r=new JPanel(new BorderLayout(8,0)); r.setBackground(new Color(16,26,50,200));
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE,34));
        JLabel l=new JLabel(lbl); l.setForeground(UI_MUTED); l.setFont(FN); l.setPreferredSize(new Dimension(82,20));
        r.add(l,BorderLayout.WEST); r.add(c,BorderLayout.CENTER);
        r.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(42,62,102),1),BorderFactory.createEmptyBorder(2,6,2,6)));
        return r;
    }
    void styleCombo(JComboBox<?> c){
        c.setBackground(new Color(22,38,72)); c.setForeground(UI_TEXT); c.setFont(FN_B);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));
        c.setRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(JList<?> lst,Object val,int idx,boolean sel,boolean foc){
                JLabel lbl=(JLabel)super.getListCellRendererComponent(lst,val,idx,sel,foc);
                lbl.setFont(FN_B); lbl.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
                lbl.setBackground(sel?new Color(50,88,148):new Color(22,38,72));
                lbl.setForeground(sel?new Color(235,245,255):UI_TEXT); return lbl;
            }
        });
    }
    JButton btn(String t,ActionListener a){ return btn(t,a,new Color(26,42,80)); }
    JButton btn(String t,ActionListener a,Color bg){
        JButton b=new JButton(t){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                Color base=getModel().isPressed()?bg.darker():getModel().isRollover()?bg.brighter():bg;
                g2.setPaint(new GradientPaint(0,0,base.brighter(),0,getHeight(),base.darker()));
                g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                g2.setColor(new Color(120,160,220,100)); g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                g2.dispose(); super.paintComponent(g);
            }
        };
        b.setForeground(UI_TEXT); b.setFont(FN_B); b.setContentAreaFilled(false); b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(5,10,5,10));
        b.setFocusPainted(false); b.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));
        b.setAlignmentX(CENTER_ALIGNMENT); b.addActionListener(a);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return b;
    }
    JToggleButton tog(String t,boolean def,java.util.function.Consumer<Boolean> cb){
        JToggleButton b=new JToggleButton(t,def){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                Color base=isSelected()?new Color(48,96,158):new Color(28,46,84);
                if(getModel().isPressed()) base=base.darker();
                else if(getModel().isRollover()) base=base.brighter();
                g2.setPaint(new GradientPaint(0,0,base.brighter(),0,getHeight(),base.darker()));
                g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                g2.setColor(new Color(120,158,220,100)); g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                g2.dispose(); super.paintComponent(g);
            }
        };
        b.setFont(FN_B); b.setContentAreaFilled(false); b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
        b.setFocusPainted(false); b.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));
        b.setAlignmentX(CENTER_ALIGNMENT);
        Runnable sync=()->b.setForeground(b.isSelected()?new Color(235,248,255):new Color(180,200,230));
        sync.run(); b.addActionListener(e->{sync.run();cb.accept(b.isSelected());});
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return b;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SIMULATION PANEL
    // ══════════════════════════════════════════════════════════════════════════
    class SimPanel extends JPanel implements ActionListener {
        List<Body>    bodies  = new CopyOnWriteArrayList<>();
        List<Particle> sparks = new CopyOnWriteArrayList<>();
        boolean paused=false, showOrbits=true, showTrails=true;
        int theme=0;
        double sunAlive=1.0, sunX, sunY, sunM=2000, sunR=28;
        boolean supernovaActive=false;
        double supernovaAge=0, supernovaShockR=0, supernovaFade=0;
        double[] stX,stY,stR,stB;
        javax.swing.Timer loop;
        Point drag0=null,drag1=null;
        long frame=0;

        // close-up
        Body cuBody=null; double cuBlend=0,cuTarget=0,cuSpin=0;

        // shuttle controls — FIX #7: use global key listener so focus doesn't matter
        boolean kW=false,kA=false,kS=false,kD=false;
        Body shuttle=null;

        // texture cache
        Map<String,BufferedImage> texCache=new ConcurrentHashMap<>();
        Map<String,Boolean> texResolveQueued=new ConcurrentHashMap<>();
        static final String[] BASE_TEX_KEYS={"earth","mars","jupiter","saturn","venus","mercury","neptune","uranus","moon"};
        static final Map<String,String> REMOTE_TEX_URLS=Map.ofEntries(
            Map.entry("earth","https://www.solarsystemscope.com/textures/download/2k_earth_daymap.jpg"),
            Map.entry("mars","https://www.solarsystemscope.com/textures/download/2k_mars.jpg"),
            Map.entry("jupiter","https://www.solarsystemscope.com/textures/download/2k_jupiter.jpg"),
            Map.entry("saturn","https://www.solarsystemscope.com/textures/download/2k_saturn.jpg"),
            Map.entry("venus","https://www.solarsystemscope.com/textures/download/2k_venus_surface.jpg"),
            Map.entry("mercury","https://www.solarsystemscope.com/textures/download/2k_mercury.jpg"),
            Map.entry("neptune","https://www.solarsystemscope.com/textures/download/2k_neptune.jpg"),
            Map.entry("uranus","https://www.solarsystemscope.com/textures/download/2k_uranus.jpg"),
            Map.entry("moon","https://www.solarsystemscope.com/textures/download/2k_moon.jpg")
        );

        SimPanel(){
            setBackground(Color.BLACK); setOpaque(true);
            setFocusable(true); setFocusTraversalKeysEnabled(false);
            Random rng=new Random(); int ns=300;
            stX=new double[ns];stY=new double[ns];stR=new double[ns];stB=new double[ns];
            for(int i=0;i<ns;i++){stX[i]=rng.nextDouble();stY[i]=rng.nextDouble();stR[i]=rng.nextDouble()*1.4+0.3;stB[i]=rng.nextDouble()*0.5+0.2;}
            primeTextureCache();
            addMouseListener(new MouseAdapter(){
                public void mousePressed(MouseEvent e){requestFocusInWindow();drag0=e.getPoint();drag1=e.getPoint();}
                public void mouseReleased(MouseEvent e){
                    if(drag0==null) return;
                    int dx=e.getX()-drag0.x,dy=e.getY()-drag0.y;
                    double len=Math.hypot(dx,dy);
                    if(len<6){
                        // close-up toggle
                        if(cuBlend>0.05||cuTarget>0.5){
                            if(!cuHit(e.getX(),e.getY())) cuTarget=0;
                            drag0=null;drag1=null;repaint();return;
                        }
                        Body hit=bodyAt(e.getX(),e.getY());
                        if(hit!=null&&canCu(hit)){
                            cuTarget=(cuTarget>0.5&&cuBody==hit)?0:1; cuBody=hit;
                        } else if(cuTarget<0.1){
                            spawn(drag0.x,drag0.y,0,0);
                        }
                    } else if(cuBlend<0.08&&cuTarget<0.5){
                        spawn(drag0.x,drag0.y,(e.getX()-drag0.x)*0.1,(e.getY()-drag0.y)*0.1);
                    }
                    drag0=null;drag1=null;repaint();
                }
            });
            addMouseMotionListener(new MouseMotionAdapter(){
                public void mouseDragged(MouseEvent e){drag1=e.getPoint();repaint();}
            });
            // FIX #7: register key bindings on the window — works regardless of focus
            setupKeys();
            loop=new javax.swing.Timer(16,this); loop.start();
        }

        void setupKeys(){
            bindKey(KeyEvent.VK_W,true, ()->kW=true);  bindKey(KeyEvent.VK_W,false,()->kW=false);
            bindKey(KeyEvent.VK_A,true, ()->kA=true);  bindKey(KeyEvent.VK_A,false,()->kA=false);
            bindKey(KeyEvent.VK_S,true, ()->kS=true);  bindKey(KeyEvent.VK_S,false,()->kS=false);
            bindKey(KeyEvent.VK_D,true, ()->kD=true);  bindKey(KeyEvent.VK_D,false,()->kD=false);
        }
        void bindKey(int kc,boolean press,Runnable r){
            String id="sh-"+kc+(press?"-p":"-r");
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(kc,0,!press),id);
            getActionMap().put(id,new AbstractAction(){public void actionPerformed(ActionEvent e){r.run();}});
        }

        double cx(){return getWidth()>10?getWidth()/2.0:(W-CTRL_W)/2.0;}
        double cy(){return getHeight()>10?getHeight()/2.0:H/2.0;}
        boolean canCu(Body b){return b.type==BT.PLANET||b.type==BT.MOON||b.type==BT.ASTEROID;}
        Body bodyAt(int mx,int my){
            for(int i=bodies.size()-1;i>=0;i--){
                Body b=bodies.get(i); if(!canCu(b)||b.dead) continue;
                double dx=mx-b.x,dy=my-b.y,hr=Math.max(7,b.radius*1.3);
                if(dx*dx+dy*dy<=hr*hr) return b;
            }
            return null;
        }
        double cuCX(Body b){return lerp(b.x,getWidth()*.5,smooth(cuBlend));}
        double cuCY(Body b){return lerp(b.y,getHeight()*.52,smooth(cuBlend));}
        double cuR(Body b){return lerp(Math.max(9,b.radius*1.6),Math.min(getWidth(),getHeight())*.34,smooth(cuBlend));}
        boolean cuHit(int mx,int my){if(cuBody==null)return false;double dx=mx-cuCX(cuBody),dy=my-cuCY(cuBody),r=cuR(cuBody);return dx*dx+dy*dy<=r*r;}
        double lerp(double a,double b,double t){return a+(b-a)*t;}
        double smooth(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
        Color mix(Color a,Color b,double t){
            t=Math.max(0,Math.min(1,t));
            return new Color((int)(a.getRed()+(b.getRed()-a.getRed())*t),
                (int)(a.getGreen()+(b.getGreen()-a.getGreen())*t),
                (int)(a.getBlue()+(b.getBlue()-a.getBlue())*t),
                (int)(a.getAlpha()+(b.getAlpha()-a.getAlpha())*t));
        }

        // ── scene setup ───────────────────────────────────────────────────────
        void createSun(){
            sunM=sunMassSlider.getValue(); sunR=sunRadSlider.getValue();
            sunX=cx(); sunY=cy(); sunAlive=1.0;
            supernovaActive=false; supernovaAge=0; supernovaShockR=0; supernovaFade=0;
        }

        void loadSolarSystem(){
            bodies.clear();sparks.clear();createSun();
            Object[][] data={
                {"Mercury",52,4,5,0.21,0},{"Venus",80,7,2,0.01,0},
                {"Earth",112,8,0,0.02,1},{"Mars",148,6,1,0.09,2},
                {"Jupiter",210,19,3,0.05,3},{"Saturn",270,16,3,0.06,0},
                {"Uranus",320,12,0,0.05,0},{"Neptune",365,10,0,0.01,0}
            };
            for(Object[] d:data){
                double dist=(double)(int)d[1],r=(double)(int)d[2],ecc=(double)d[4]; int ci=(int)d[3];
                Body b=new Body(sunX+dist,sunY,r*r*0.4,r,BT.PLANET,ci);
                b.name=(String)d[0]; b.orbitEcc=ecc; b.orbit(sunX,sunY,sunM);
                if(d[0].equals("Saturn")){b.rings=true;b.ringTilt=0.25;}
                if(d[0].equals("Uranus")){b.rings=true;b.ringTilt=0.6;}
                if(d[0].equals("Earth"))b.setMoons(1);
                if(d[0].equals("Mars"))b.setMoons(2);
                if(d[0].equals("Jupiter"))b.setMoons(3);
                bodies.add(b);
            }
        }

        void recompute(){
            sunM=sunMassSlider.getValue(); sunR=sunRadSlider.getValue();
            for(Body b:bodies) if(b.kepler) b.orbit(sunX,sunY,sunM);
        }

        void addOrbiter(){
            double ang=Math.random()*Math.PI*2,dist=80+Math.random()*220;
            Body b=new Body(sunX+Math.cos(ang)*dist,sunY+Math.sin(ang)*dist,
                20+Math.random()*60,6+Math.random()*10,BT.PLANET,(int)(Math.random()*PAL.length));
            b.orbitEcc=Math.random()*0.3; b.orbit(sunX,sunY,sunM); bodies.add(b);
        }

        void scatterAsteroids(){
            for(int i=0;i<42;i++){
                double ang=Math.random()*Math.PI*2,dist=155+Math.random()*75;
                Body b=new Body(sunX+Math.cos(ang)*dist,sunY+Math.sin(ang)*dist,1,2+Math.random()*2,BT.ASTEROID,5);
                b.orbitEcc=Math.random()*0.2; b.orbit(sunX,sunY,sunM); bodies.add(b);
            }
        }

        void bigBang(){
            bodies.clear();sparks.clear();createSun();
            Random rng=new Random();
            for(int i=0;i<24;i++){
                double ang=rng.nextDouble()*Math.PI*2,spd=2+rng.nextDouble()*7;
                double r=4+rng.nextDouble()*14,dist=20+rng.nextDouble()*55;
                Body b=new Body(sunX+Math.cos(ang)*dist,sunY+Math.sin(ang)*dist,r*r*0.3,r,BT.PLANET,rng.nextInt(PAL.length));
                b.vx=Math.cos(ang)*spd; b.vy=Math.sin(ang)*spd; bodies.add(b);
            }
        }

        void clearAll(){bodies.clear();sparks.clear();createSun();}

        void triggerSupernova(){
            if(supernovaActive||sunAlive<=0.02) return;
            supernovaActive=true;
            supernovaAge=0;
            supernovaShockR=Math.max(8,sunR*1.1);
            supernovaFade=1.0;
            sunAlive=0;
            for(Body b:bodies) b.kepler=false;
            for(int i=0;i<220;i++){
                double ang=Math.random()*Math.PI*2,spd=2+Math.random()*13;
                Color c=i%4==0?new Color(255,245,210):new Color(255,120+(int)(100*Math.random()),30+(int)(40*Math.random()));
                sparks.add(new Particle(sunX,sunY,c,Math.cos(ang)*spd,Math.sin(ang)*spd,2.2f));
            }
        }

        void spawn(double x,double y,double vx,double vy){
            String ts=(String)bodyTypeCombo.getSelectedItem(); if(ts==null) ts="Planet";
            int ci=colorCombo.getSelectedIndex();
            double r=pSizeSlider.getValue(),ecc=eccSlider.getValue()/100.0;
            BT t; double mass;
            switch(ts){
                case "Space Shuttle": t=BT.SHUTTLE;    mass=8+r*0.45; r=Math.max(6,Math.min(11,r*0.7)); break;
                case "Black Hole":    t=BT.BLACK_HOLE; mass=4000+r*100; r=Math.max(r,14); break;
                case "Pulsar":        t=BT.PULSAR;     mass=900+r*40; break;
                case "Wormhole":      t=BT.WORMHOLE;   mass=200; r=Math.max(r,16); break;
                case "Asteroid Belt": t=BT.ASTEROID;   mass=0.5; r=Math.max(2,r/4); break;
                case "Moon":          t=BT.MOON;       mass=2+r; break;
                default:              t=BT.PLANET;     mass=r*r*0.4; break;
            }
            Body b=new Body(x,y,mass,r,t,ci); b.orbitEcc=ecc; b.vx=vx; b.vy=vy;
            if(t==BT.SHUTTLE){ b.shuttleAngle=(Math.hypot(vx,vy)>0.1?Math.atan2(vy,vx):-Math.PI/2); shuttle=b; }
            if(vx==0&&vy==0&&t!=BT.BLACK_HOLE&&t!=BT.PULSAR&&t!=BT.WORMHOLE&&t!=BT.SHUTTLE)
                b.orbit(sunX,sunY,sunM);
            if(t==BT.WORMHOLE){
                for(Body bd:bodies) if(bd.type==BT.WORMHOLE&&bd.whPartner==null){b.whPartner=bd;bd.whPartner=b;break;}
            }
            bodies.add(b);
        }

        // ── physics ───────────────────────────────────────────────────────────
        void shuttleControls(double dt){
            if(shuttle==null||shuttle.dead||!bodies.contains(shuttle)){shuttle=null;return;}
            Body s=shuttle; s.kepler=false;
            s.thrustMain=kW; s.thrustLeft=kA; s.thrustRight=kD; s.thrustReverse=kS;
            if(kA) s.shuttleAngle-=0.07*dt;
            if(kD) s.shuttleAngle+=0.07*dt;
            if(kW){ s.vx+=Math.cos(s.shuttleAngle)*0.17*dt; s.vy+=Math.sin(s.shuttleAngle)*0.17*dt; }
            if(kS){ s.vx-=Math.cos(s.shuttleAngle)*0.11*dt; s.vy-=Math.sin(s.shuttleAngle)*0.11*dt; }
            s.vx*=0.998; s.vy*=0.998;
        }

        void updateSupernova(double dt){
            if(!supernovaActive&&supernovaFade<=0) return;
            if(supernovaActive){
                supernovaAge+=dt;
                supernovaShockR=sunR*1.2+supernovaAge*3.8;
                double shellW=Math.max(16,sunR*1.2+supernovaAge*0.14);
                int burst=Math.max(2,(int)(3+dt*2));
                for(int i=0;i<burst;i++){
                    double ang=Math.random()*Math.PI*2,spd=5+Math.random()*10;
                    Color c=new Color(255,130+(int)(90*Math.random()),40+(int)(40*Math.random()));
                    sparks.add(new Particle(sunX,sunY,c,Math.cos(ang)*spd,Math.sin(ang)*spd,2f));
                }
                for(Body b:bodies){
                    if(b.dead||b.type==BT.BLACK_HOLE||b.type==BT.WORMHOLE) continue;
                    double dx=b.x-sunX,dy=b.y-sunY,d=Math.hypot(dx,dy)+0.001;
                    double shellHit=Math.max(0,1-Math.abs(d-supernovaShockR)/shellW);
                    if(shellHit<=0) continue;
                    double impulse=(0.2+shellHit*1.4)*dt;
                    b.vx+=(dx/d)*impulse; b.vy+=(dy/d)*impulse;
                    b.kepler=false;
                    if((b.type==BT.PLANET||b.type==BT.MOON||b.type==BT.ASTEROID)&&shellHit>0.6){
                        b.dead=true; sparks(b.x,b.y,14,b.ci);
                    } else if(b.type==BT.SHUTTLE&&shellHit>0.72){
                        b.dead=true; sparks(b.x,b.y,9,b.ci);
                    }
                }
                supernovaFade=Math.max(0.35,1.0-supernovaAge/260.0);
                if(supernovaAge>220) supernovaActive=false;
            } else {
                supernovaFade=Math.max(0,supernovaFade-0.012*dt);
                supernovaShockR+=2.4*dt;
            }
        }

        void tick(double dt){
            sunX=cx(); sunY=cy();
            sunM=sunMassSlider.getValue(); sunR=sunRadSlider.getValue();
            shuttleControls(dt);

            // FIX #1/#2: correct kepler step
            for(Body b:bodies){
                if(!b.kepler||b.type==BT.SHUTTLE) continue;
                double e=Math.min(b.orbitEcc,0.85);
                double r_=b.orbitDist*(1-e*e)/(1+e*Math.cos(b.orbitAngle));
                b.orbitAngle+=b.orbitSpeed*(b.orbitDist/Math.max(r_,0.1))*dt;
                Point2D.Double pos=b.kpos(sunX,sunY);
                b.vx=pos.x-b.x; b.vy=pos.y-b.y; b.x=pos.x; b.y=pos.y;
            }

            // n-body for free bodies
            List<Body> all=new ArrayList<>(bodies);
            for(Body a:bodies){
                if(a.kepler) continue;
                double ax=0,ay=0;
                // sun gravity
                double sdx=sunX-a.x,sdy=sunY-a.y,sd=Math.hypot(sdx,sdy)+1;
                ax+=G*sunM*sunAlive*sdx/(sd*sd*sd); ay+=G*sunM*sunAlive*sdy/(sd*sd*sd);
                for(Body b2:all){
                    if(b2==a) continue;
                    double dx=b2.x-a.x,dy=b2.y-a.y,d=Math.hypot(dx,dy)+0.5;
                    double gm=b2.type==BT.BLACK_HOLE?G*b2.mass*6:G*b2.mass;
                    ax+=gm*dx/(d*d*d); ay+=gm*dy/(d*d*d);
                }
                a.vx+=ax*dt; a.vy+=ay*dt;
                double spd=Math.hypot(a.vx,a.vy); if(spd>30){a.vx*=30/spd;a.vy*=30/spd;}
            }

            updateSupernova(dt);

            // wormhole teleport
            for(Body a:bodies){
                if(a.kepler||a.type==BT.WORMHOLE) continue;
                for(Body wh:bodies){
                    if(wh.type!=BT.WORMHOLE||wh.whPartner==null) continue;
                    if(Math.hypot(a.x-wh.x,a.y-wh.y)<wh.radius*0.9){
                        Body ex=wh.whPartner; double oa=Math.atan2(a.vy,a.vx);
                        a.x=ex.x+Math.cos(oa)*ex.radius*1.5; a.y=ex.y+Math.sin(oa)*ex.radius*1.5;
                        for(int i=0;i<14;i++) sparks.add(new Particle(ex.x,ex.y,new Color(80,200,255)));
                        break;
                    }
                }
            }

            // integrate + secondary animation
            for(Body a:bodies){
                if(!a.kepler){a.x+=a.vx*dt;a.y+=a.vy*dt;}
                for(int m=0;m<a.moons;m++) a.mAngle[m]+=a.mSpeed[m]*dt*2;
                if(a.type==BT.BLACK_HOLE) a.bhRot+=0.018*dt;
                if(a.type==BT.PULSAR)     a.pulsarPhase+=(Math.PI*2/(a.pulsarPeriod*60))*dt;
                if(a.type==BT.WORMHOLE)   a.whPulse=(a.whPulse+0.04*dt)%(Math.PI*2);
                // spaghettification
                if(a.spag&&a.spagTarget!=null&&!a.spagTarget.dead){
                    double sdx=a.spagTarget.x-a.x,sdy=a.spagTarget.y-a.y,sd=Math.hypot(sdx,sdy);
                    double raw=Math.min(1,Math.max(0,1-sd/(a.spagTarget.radius*6)));
                    a.spagRaw=raw; a.spagP=raw*raw*(3-2*raw); a.spagA=Math.atan2(sdy,sdx);
                    if(a.spagP>0.4&&sd>0){a.vx+=(sdx/sd)*0.2*dt;a.vy+=(sdy/sd)*0.2*dt;}
                } else if(a.spag){a.spag=false;a.spagP=0;a.spagTarget=null;}
                // trail
                a.trail.add(new Point2D.Double(a.x,a.y));
                if(a.trail.size()>MAX_TRAIL) a.trail.remove(0);
            }

            // collisions & absorptions
            List<Body> rm=new ArrayList<>();
            for(int i=0;i<bodies.size();i++){
                Body a=bodies.get(i); if(a.dead){rm.add(a);continue;}

                // BH eats sun
                if(a.type==BT.BLACK_HOLE&&sunAlive>0){
                    double d=Math.hypot(a.x-sunX,a.y-sunY);
                    if(d<sunR*sunAlive+a.radius){
                        double bite=Math.min(sunAlive,0.04*dt); sunAlive=Math.max(0,sunAlive-bite);
                        a.mass+=sunM*bite*0.8; a.radius+=bite*sunR*0.08; // no cap
                        bhJet(a); sunMassSlider.setValue(Math.max(200,(int)(sunM*sunAlive)));
                        if(sunAlive<0.01){sunAlive=0;for(int k=0;k<30;k++)sparks.add(new Particle(sunX,sunY,new Color(255,200,60)));}
                    }
                }

                // sun physics for planets
                if(a.type!=BT.BLACK_HOLE&&a.type!=BT.PULSAR&&a.type!=BT.WORMHOLE&&sunAlive>0.1){
                    double sdx=a.x-sunX,sdy=a.y-sunY,sd=Math.hypot(sdx,sdy);
                    double surf=sunR*sunAlive, corona=surf+a.radius*1.6;
                    if(a.type==BT.PLANET&&sd<=surf+a.radius){
                        sparks(a.x,a.y,18,a.ci);
                        for(int k=0;k<8;k++)
                            sparks.add(new Particle(sunX+(Math.random()-.5)*sunR*.9,sunY+(Math.random()-.5)*sunR*.9,new Color(255,170+(int)(80*Math.random()),40+(int)(40*Math.random()))));
                        a.dead=true;rm.add(a);continue;
                    }
                    if(sd<corona){
                        double nx=sd>0?sdx/sd:1,ny=sd>0?sdy/sd:0;
                        double depth=Math.max(0,Math.min(1,(surf+a.radius*1.1-sd)/(a.radius*2.1+surf*0.35)));
                        a.vx*=Math.max(0,1-(0.06+0.28*depth)*dt); a.vy*=Math.max(0,1-(0.06+0.28*depth)*dt);
                        a.kepler=false;
                        double vr=a.vx*nx+a.vy*ny;
                        if(vr<0){double res=0.18+0.42*(1-depth);a.vx-=(1+res)*vr*nx;a.vy-=(1+res)*vr*ny;}
                        double phot=surf+a.radius*0.55;
                        if(sd<phot){a.x+=nx*(phot-sd);a.y+=ny*(phot-sd);}
                        if(depth>0.18){a.mass*=Math.max(0.90,1-0.06*depth*dt);a.radius=Math.max(1.4,a.radius*(1-0.025*depth*dt));}
                        if(Math.random()<0.15+depth*0.5)
                            sparks.add(new Particle(a.x-nx*a.radius*.35+(Math.random()-.5)*2,a.y-ny*a.radius*.35+(Math.random()-.5)*2,new Color(255,170+(int)(80*Math.random()),50+(int)(40*Math.random()))));
                        if(sd<surf*0.78||depth>0.92&&Math.hypot(a.vx,a.vy)<2.2||a.radius<1.55||a.mass<0.9){
                            sparks(a.x,a.y,10,a.ci); a.dead=true;rm.add(a);continue;
                        }
                    }
                }

                // body-body
                for(int j=i+1;j<bodies.size();j++){
                    Body b=bodies.get(j); if(b.dead) continue;
                    double d=Math.hypot(b.x-a.x,b.y-a.y);
                    if(d<(a.radius+b.radius)*0.75){
                        if(a.type==BT.SHUTTLE||b.type==BT.SHUTTLE){
                            Body sh=a.type==BT.SHUTTLE?a:b, ot=sh==a?b:a;
                            double nx=d>0?(b.x-a.x)/d:1,ny=d>0?(b.y-a.y)/d:0;
                            double ov=(sh.radius+ot.radius)*0.75-d;
                            if(ov>0){sh.x-=nx*ov*.5;sh.y-=ny*ov*.5;}
                            double rv=sh.vx*nx+sh.vy*ny;
                            if(rv>0){sh.vx-=2*rv*nx*0.82;sh.vy-=2*rv*ny*0.82;}
                            sh.kepler=false;
                        } else if(a.type==BT.BLACK_HOLE||b.type==BT.BLACK_HOLE){
                            Body bh=a.type==BT.BLACK_HOLE?a:b, ot=bh==a?b:a;
                            if(ot.spagP>=0.88||ot.type==BT.BLACK_HOLE||ot.type==BT.PULSAR){
                                bh.mass+=ot.mass*0.92; bh.radius+=ot.radius*0.12; // no cap
                                bhJet(bh); ot.dead=true;rm.add(ot);
                            }
                        } else if(a.type!=BT.WORMHOLE&&b.type!=BT.WORMHOLE){
                            Body big=a.mass>=b.mass?a:b,sm=big==a?b:a;
                            double tm=big.mass+sm.mass*0.85;
                            big.vx=(big.vx*big.mass+sm.vx*sm.mass)/tm; big.vy=(big.vy*big.mass+sm.vy*sm.mass)/tm;
                            big.mass=tm; big.radius=Math.min(Math.pow(tm,.38)*1.1,42); big.kepler=false;
                            sparks((big.x+sm.x)/2,(big.y+sm.y)/2,12,sm.ci); sm.dead=true;rm.add(sm);
                        }
                        break;
                    }
                }

                // BH spag zone
                if(a.type==BT.BLACK_HOLE){
                    for(Body b:bodies){
                        if(b==a||b.dead||b.type==BT.BLACK_HOLE||b.type==BT.WORMHOLE) continue;
                        double d=Math.hypot(a.x-b.x,a.y-b.y);
                        if(d<a.radius*6){b.spag=true;b.spagTarget=a;b.kepler=false;}
                        else if(d<200) b.kepler=false;
                    }
                    if(sunAlive>0.08){
                        double sdx=a.x-sunX,sdy=a.y-sunY,sd=Math.hypot(sdx,sdy);
                        if(sd<a.radius*9&&sd>a.radius&&Math.random()<0.35)
                            sparks.add(new Particle(sunX+(Math.random()-.5)*sunR,sunY+(Math.random()-.5)*sunR,
                                new Color(255,170,20),(sdx/sd)*4+(Math.random()-.5),(sdy/sd)*4+(Math.random()-.5),2f));
                    }
                }

                // OOB
                if(!a.kepler&&a.type!=BT.WORMHOLE&&(a.x<-600||a.x>getWidth()+600||a.y<-600||a.y>getHeight()+600)){a.dead=true;rm.add(a);}
            }
            bodies.removeAll(rm);
            sparks.removeIf(s->{s.tick(dt);return s.dead();});
        }

        void sparks(double x,double y,int n,int ci){Color[]p=PAL[ci%PAL.length];for(int i=0;i<n;i++)sparks.add(new Particle(x,y,p[0]));}
        void bhJet(Body bh){for(int i=0;i<5;i++){double a=bh.bhRot+(i<3?0:Math.PI)+(Math.random()-.5)*.5;sparks.add(new Particle(bh.x,bh.y,new Color(160,80,255),Math.cos(a)*(3+Math.random()*5),Math.sin(a)*(3+Math.random()*5),1.8f));}}

        // ── close-up animation ────────────────────────────────────────────────
        void updateCu(){
            if(cuBody!=null&&(cuBody.dead||!bodies.contains(cuBody))) cuTarget=0;
            cuBlend+=(cuTarget-cuBlend)*0.12;
            if(Math.abs(cuTarget-cuBlend)<0.001) cuBlend=cuTarget;
            if(cuBlend>0.001&&cuBody!=null) cuSpin+=0.007+Math.hypot(cuBody.vx,cuBody.vy)*0.0018;
            if(cuBlend<0.002&&cuTarget==0) cuBody=null;
        }

        // ── textures ──────────────────────────────────────────────────────────
        void primeTextureCache(){
            for(String key:BASE_TEX_KEYS){
                if("saturn".equals(key)||"neptune".equals(key)||"uranus".equals(key))
                    downloadedTextureFile(key).delete();
                texCache.put(key,buildQuickPlaceholderTex(key));
                resolveTextureAsync(key);
            }
        }
        void resolveTextureAsync(String key){
            if(texResolveQueued.putIfAbsent(key,Boolean.TRUE)!=null) return;
            Thread t=new Thread(() -> {
                BufferedImage img=loadTextureImage(key);
                if(img==null) img=loadDownloadedTextureFromDisk(key);
                boolean fallback=false;
                if(img==null){
                    img=downloadPhotorealTexture(key);
                }
                if(img==null){
                    img=buildTex(key);
                    fallback=true;
                }
                texCache.put(key,img);
                if(fallback){
                    // Allow retry on later clicks in case network/files become available.
                    texResolveQueued.remove(key);
                }
                SwingUtilities.invokeLater(this::repaint);
            },"planet-texture-loader-"+key);
            t.setDaemon(true);
            t.start();
        }
        BufferedImage buildQuickPlaceholderTex(String key){
            int w=160,h=80;
            BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=img.createGraphics();
            Color[] p=texPal(key);
            g.setPaint(new GradientPaint(0,0,p[0],0,h,p[2]));
            g.fillRect(0,0,w,h);
            g.setColor(new Color(255,255,255,28));
            g.drawLine(0,h/3,w,h/3);
            g.drawLine(0,h*2/3,w,h*2/3);
            g.dispose();
            return img;
        }
        String texKey(Body b){
            if(b.name!=null&&!b.name.isEmpty()){
                String n=b.name.toLowerCase();
                for(String k:new String[]{"earth","mars","jupiter","saturn","venus","mercury","neptune","uranus","moon"})
                    if(n.contains(k)) return k;
            }
            String[]keys={"earth","mars","neptune","jupiter","saturn","moon","venus","mercury"};
            return keys[Math.floorMod(b.ci,keys.length)];
        }
        Color[] texPal(String key){
            switch(key){
                case "earth":   return new Color[]{new Color(38,84,162),new Color(44,140,82),new Color(18,42,94)};
                case "mars":    return new Color[]{new Color(176,92,54),new Color(142,62,36),new Color(84,36,24)};
                case "jupiter": return new Color[]{new Color(209,171,132),new Color(180,128,86),new Color(102,70,48)};
                case "saturn":  return new Color[]{new Color(224,202,148),new Color(182,152,99),new Color(102,84,58)};
                case "venus":   return new Color[]{new Color(236,196,114),new Color(198,146,72),new Color(120,78,34)};
                case "mercury": return new Color[]{new Color(176,170,162),new Color(132,126,118),new Color(76,72,68)};
                case "moon":    return new Color[]{new Color(188,190,194),new Color(146,148,154),new Color(94,96,102)};
                case "uranus":  return new Color[]{new Color(124,198,213),new Color(92,164,182),new Color(54,102,122)};
                case "neptune": return new Color[]{new Color(58,95,196),new Color(46,71,165),new Color(24,38,112)};
                default:        return new Color[]{new Color(94,140,215),new Color(60,106,184),new Color(24,54,120)};
            }
        }
        BufferedImage getTex(Body b){
            String key=texKey(b);
            BufferedImage cached=texCache.get(key);
            if(cached==null){
                cached=buildQuickPlaceholderTex(key);
                texCache.put(key,cached);
            }
            resolveTextureAsync(key);
            return cached;
        }
        BufferedImage loadTextureImage(String key){
            String[] relPaths={
                "textures/"+key+".png",
                "textures/"+key+".jpg",
                "textures/"+key+".jpeg",
                "assets/textures/"+key+".png",
                "assets/textures/"+key+".jpg",
                "assets/textures/"+key+".jpeg",
                "images/"+key+".png",
                "images/"+key+".jpg",
                "images/"+key+".jpeg",
                "src/backend/textures/"+key+".png",
                "src/backend/textures/"+key+".jpg",
                "src/backend/textures/"+key+".jpeg",
                "src/frontend/textures/"+key+".png",
                "src/frontend/textures/"+key+".jpg",
                "src/frontend/textures/"+key+".jpeg"
            };

            // Try classpath resources first.
            for(String rel:relPaths){
                BufferedImage fromRes=readTextureFromResource("/"+rel);
                if(fromRes!=null) return fromRes;
            }

            // Then try workspace-relative file paths.
            for(String rel:relPaths){
                File f=new File(rel);
                if(!f.isFile()) continue;
                BufferedImage fromFile=readTextureFromFile(f);
                if(fromFile!=null) return fromFile;
            }
            return null;
        }
        File downloadedTextureFile(String key){
            return new File("textures/cache/"+key+".png");
        }
        BufferedImage loadDownloadedTextureFromDisk(String key){
            File f=downloadedTextureFile(key);
            if(!f.isFile()) return null;
            return readTextureFromFile(f);
        }
        void saveDownloadedTextureToDisk(String key,BufferedImage img){
            if(img==null) return;
            File f=downloadedTextureFile(key);
            File parent=f.getParentFile();
            if(parent!=null&&!parent.exists()) parent.mkdirs();
            try{ ImageIO.write(img,"png",f); } catch(IOException ignored){}
        }
        BufferedImage downloadPhotorealTexture(String key){
            String url=REMOTE_TEX_URLS.get(key);
            if(url==null||url.isBlank()) return null;
            try{
                java.net.URLConnection conn=java.net.URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent","Mozilla/5.0");
                try(InputStream is=conn.getInputStream()){
                    BufferedImage img=normalizeTexture(ImageIO.read(is));
                    if(img!=null) saveDownloadedTextureToDisk(key,img);
                    return img;
                }
            } catch(Exception ignored){
                return null;
            }
        }
        BufferedImage readTextureFromResource(String resourcePath){
            try(InputStream is=SolarSystemEngine.class.getResourceAsStream(resourcePath)){
                if(is==null) return null;
                return normalizeTexture(ImageIO.read(is));
            } catch(IOException ignored){
                return null;
            }
        }
        BufferedImage readTextureFromFile(File file){
            try{
                return normalizeTexture(ImageIO.read(file));
            } catch(IOException ignored){
                return null;
            }
        }
        BufferedImage normalizeTexture(BufferedImage src){
            if(src==null||src.getWidth()<2||src.getHeight()<2) return null;
            int sw=src.getWidth(),sh=src.getHeight();
            int tw=sw,th=sh;
            final int MAX_W=1024,MAX_H=512;
            if(sw>MAX_W||sh>MAX_H){
                double scale=Math.min((double)MAX_W/sw,(double)MAX_H/sh);
                tw=Math.max(2,(int)Math.round(sw*scale));
                th=Math.max(2,(int)Math.round(sh*scale));
            }
            BufferedImage out=new BufferedImage(tw,th,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src,0,0,tw,th,null);
            g.dispose();
            return out;
        }
        BufferedImage buildTex(String key){
            int w=1024,h=512; BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
            Graphics2D tg=img.createGraphics(); tg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Color[]p=texPal(key);
            tg.setPaint(new GradientPaint(0,0,p[0],0,h,p[2])); tg.fillRect(0,0,w,h);
            for(int y=0;y<h;y++){
                double lat=(double)y/h,wave=.5+.5*Math.sin(lat*22+Math.sin(lat*4)*2.8);
                Color band=mix(mix(p[1],p[0],wave),p[2],lat*.55);
                tg.setColor(new Color(band.getRed(),band.getGreen(),band.getBlue(),55)); tg.drawLine(0,y,w,y);
            }
            Random rng=new Random(key.hashCode()*41L+17L);
            for(int i=0;i<180;i++){
                int cx2=rng.nextInt(w),cy2=rng.nextInt(h),rw=12+rng.nextInt(96),rh=6+rng.nextInt(44);
                Color bl=mix(p[0],p[2],rng.nextDouble()*.7); tg.setColor(new Color(bl.getRed(),bl.getGreen(),bl.getBlue(),20+rng.nextInt(55)));
                tg.fillOval(cx2-rw/2,cy2-rh/2,rw,rh);
            }
            if("jupiter".equals(key)||"saturn".equals(key)){
                for(int y=0;y<h;y+=14){double s=.5+.5*Math.sin(y*.11);Color c=mix(p[1],p[0],s);tg.setColor(new Color(c.getRed(),c.getGreen(),c.getBlue(),70));tg.fillRect(0,y,w,10);}
            }
            if("moon".equals(key)||"mercury".equals(key)||"mars".equals(key)){
                for(int i=0;i<120;i++){int cx2=rng.nextInt(w),cy2=rng.nextInt(h),r2=4+rng.nextInt(24);tg.setColor(new Color(0,0,0,16));tg.fillOval(cx2-r2,cy2-r2,r2*2,r2*2);tg.setColor(new Color(255,255,255,10));tg.drawOval(cx2-r2,cy2-r2,r2*2,r2*2);}
            }
            tg.dispose(); return img;
        }
        // FIX #12: safe spherical texture draw, guarded against zero-width source
        void drawSphere(Graphics2D g2,BufferedImage tex,double cx,double cy,double r,double spin){
            if(tex==null||tex.getWidth()<2||tex.getHeight()<2||r<1) return;
            int rows=Math.max(80,(int)(r*1.2)),tw=tex.getWidth(),th=tex.getHeight();
            double spinU=(spin*0.24)/(Math.PI*2.0);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            for(int i=0;i<rows;i++){
                double v=-1.0+(2.0*i)/(rows-1.0);
                double rowHalf=Math.sqrt(Math.max(0,1-v*v))*r; if(rowHalf<0.8) continue;
                int dy=(int)(cy+v*r);
                double lat=Math.asin(Math.max(-1,Math.min(1,v)));
                double uSpan=(rowHalf/r)*0.5;
                double u0=wrap01(spinU+0.5-uSpan),u1=wrap01(spinU+0.5+uSpan);
                int sx0=Math.max(0,Math.min(tw-1,(int)Math.floor(u0*tw)));
                int sx1=Math.max(1,Math.min(tw,(int)Math.ceil(u1*tw)));
                int sy=Math.max(0,Math.min(th-1,(int)((lat/Math.PI+.5)*th)));
                int dx0=(int)(cx-rowHalf),dx1=(int)(cx+rowHalf);
                if(dx1<=dx0) continue;
                if(u0<=u1){
                    if(sx1<=sx0) sx1=Math.min(tw,sx0+1);
                    g2.drawImage(tex,dx0,dy,dx1,dy+1,sx0,sy,Math.min(sx1+1,tw),Math.min(sy+1,th),null);
                } else {
                    int leftSrcW=tw-sx0;
                    int rightSrcW=sx1;
                    int totalSrcW=leftSrcW+rightSrcW;
                    if(totalSrcW<=0) continue;
                    int leftDx1=dx0+(int)Math.round((dx1-dx0)*(leftSrcW/(double)totalSrcW));
                    leftDx1=Math.max(dx0+1,Math.min(dx1-1,leftDx1));
                    if(leftSrcW>0) g2.drawImage(tex,dx0,dy,leftDx1,dy+1,sx0,sy,tw,Math.min(sy+1,th),null);
                    if(rightSrcW>0) g2.drawImage(tex,leftDx1,dy,dx1,dy+1,0,sy,Math.min(sx1,tw),Math.min(sy+1,th),null);
                }
            }
        }
        double wrap01(double u){
            u=u-Math.floor(u);
            return u<0?u+1.0:u;
        }

        // ── tick ──────────────────────────────────────────────────────────────
        @Override public void actionPerformed(ActionEvent e){
            if(!paused&&speedSlider!=null) tick(speedSlider.getValue()/10.0);
            updateCu(); frame++; repaint();
        }

        // ── paint ─────────────────────────────────────────────────────────────
        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2=(Graphics2D)g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int pw=getWidth(),ph=getHeight();
            Color a=theme==1?new Color(8,14,26):theme==2?new Color(7,7,12):new Color(5,5,18);
            Color b=theme==1?new Color(0,3,10):theme==2?new Color(2,2,6):new Color(0,0,6);
            g2.setPaint(new GradientPaint(pw/2f,ph/2f,a,pw,ph,b)); g2.fillRect(0,0,pw,ph);
            drawStars(g2,pw,ph);
            if(showOrbits) drawOrbits(g2);
            if(supernovaActive||supernovaFade>0.01) drawSupernova(g2);
            else if(sunAlive>0.02) drawSun(g2);
            for(Body bd:bodies) if(bd.type!=BT.BLACK_HOLE&&bd.type!=BT.PULSAR&&bd.type!=BT.WORMHOLE) drawBody(g2,bd);
            for(Body bd:bodies) if(bd.type==BT.PULSAR) drawPulsar(g2,bd);
            for(Body bd:bodies) if(bd.type==BT.BLACK_HOLE) drawBH(g2,bd);
            for(Body bd:bodies) if(bd.type==BT.WORMHOLE) drawWH(g2,bd);
            for(Particle sp:sparks) sp.draw(g2);
            if(cuBlend>0.001&&cuBody!=null) drawCloseup(g2,pw,ph,cuBody);
            drawArrow(g2);
            drawHUD(g2,pw,ph);
        }

        void drawStars(Graphics2D g2,int pw,int ph){
            double t=System.currentTimeMillis()*.0003;
            for(int i=0;i<stX.length;i++){
                float tw=(float)(stB[i]*(0.6+0.4*Math.sin(t*2+i)));
                if(theme==1) tw=Math.min(1f,tw*1.2f); else if(theme==2) tw=Math.max(.04f,tw*.7f);
                g2.setColor(new Color(1f,1f,1f,tw));
                g2.fillOval((int)(stX[i]*pw),(int)(stY[i]*ph),Math.max((int)(stR[i]*1.5),1),Math.max((int)(stR[i]*1.5),1));
            }
        }

        void drawOrbits(Graphics2D g2){
            g2.setColor(new Color(255,255,255,14));
            g2.setStroke(new BasicStroke(.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,1f,new float[]{4f,9f},0f));
            // FIX #5: use stored sunX/sunY (where orbits were computed), not cx()/cy()
            for(Body bd:bodies){
                if(!bd.kepler) continue;
                double e=Math.min(bd.orbitEcc,.85),aa=bd.orbitDist;
                double bx=aa*Math.sqrt(1-e*e),foc=Math.sqrt(aa*aa-bx*bx);
                g2.draw(new Ellipse2D.Double(sunX-foc-aa,sunY-bx,aa*2,bx*2));
            }
            g2.setStroke(new BasicStroke(1));
        }

        void drawSun(Graphics2D g2){
            double sr=sunR*sunAlive; if(sr<2) return;
            double t=System.currentTimeMillis()*.001,pulse=1+.025*Math.sin(t*1.6)+.012*Math.sin(t*4.7);
            int r=(int)(sr*pulse);
            // corona
            RadialGradientPaint corona=new RadialGradientPaint((float)sunX,(float)sunY,r*4.2f,
                new float[]{0f,.22f,.5f,.75f,1f},
                new Color[]{new Color(255,235,170,(int)(95*sunAlive)),new Color(255,176,84,(int)(70*sunAlive)),
                    new Color(255,118,34,(int)(40*sunAlive)),new Color(200,72,20,(int)(16*sunAlive)),new Color(120,30,8,0)});
            g2.setPaint(corona); g2.fillOval((int)(sunX-r*4.2),(int)(sunY-r*4.2),(int)(r*8.4),(int)(r*8.4));
            // granules
            for(int i=0;i<7;i++){
                double ang=t*.35+i*(Math.PI*2/7.0),sx=sunX+Math.cos(ang)*r*.78,sy=sunY+Math.sin(ang)*r*.78;
                double sw=r*(.28+.07*Math.sin(t*2.2+i)); float al=(float)(.15+.08*Math.sin(t*2.9+i*1.7));
                RadialGradientPaint gran=new RadialGradientPaint((float)sx,(float)sy,(float)sw,new float[]{0f,1f},
                    new Color[]{new Color(255,228,150,(int)(al*255)),new Color(255,128,30,0)});
                g2.setPaint(gran); g2.fillOval((int)(sx-sw),(int)(sy-sw),(int)(sw*2),(int)(sw*2));
            }
            // photosphere
            RadialGradientPaint ps=new RadialGradientPaint((float)(sunX-r*.18),(float)(sunY-r*.2),r*1.16f,
                new float[]{0f,.12f,.36f,.72f,1f},
                new Color[]{new Color(255,252,222),new Color(255,236,170),new Color(255,205,108),new Color(255,150,52),new Color(190,70,20)});
            g2.setPaint(ps); g2.fillOval((int)(sunX-r),(int)(sunY-r),r*2,r*2);
            // limb darkening
            RadialGradientPaint limb=new RadialGradientPaint((float)sunX,(float)sunY,r,new float[]{.58f,.86f,1f},
                new Color[]{new Color(0,0,0,0),new Color(120,30,10,34),new Color(35,5,0,95)});
            g2.setPaint(limb); g2.fillOval((int)(sunX-r),(int)(sunY-r),r*2,r*2);
            // specular
            RadialGradientPaint spec=new RadialGradientPaint((float)(sunX-r*.33),(float)(sunY-r*.33),r*.56f,
                new float[]{0f,1f},new Color[]{new Color(255,255,240,165),new Color(255,255,255,0)});
            g2.setPaint(spec); g2.fillOval((int)(sunX-r),(int)(sunY-r),r*2,r*2);
        }

        void drawSupernova(Graphics2D g2){
            float fade=(float)Math.max(0,Math.min(1,supernovaFade));
            float flash=supernovaActive?(float)Math.max(0,1.0-supernovaAge/80.0):0f;
            double coreR=Math.max(4,sunR*(1.3+Math.min(supernovaAge,80)*0.03));
            double glowR=Math.max(coreR*2.3,supernovaShockR*0.45);
            RadialGradientPaint core=new RadialGradientPaint((float)sunX,(float)sunY,(float)glowR,
                new float[]{0f,.14f,.42f,.72f,1f},
                new Color[]{new Color(255,255,240,(int)(230*Math.min(1f,fade+flash))),new Color(255,225,140,(int)(190*fade)),
                    new Color(255,145,55,(int)(130*fade)),new Color(220,70,30,(int)(70*fade)),new Color(50,10,6,0)});
            g2.setPaint(core);
            g2.fillOval((int)(sunX-glowR),(int)(sunY-glowR),(int)(glowR*2),(int)(glowR*2));

            double ringR=Math.max(sunR*1.2,supernovaShockR);
            if(ringR>4){
                int ringA=Math.max(0,Math.min(255,(int)(110*fade+(supernovaActive?45:0))));
                g2.setStroke(new BasicStroke(Math.max(1.5f,(float)(sunR*0.16))));
                g2.setColor(new Color(255,220,145,ringA));
                g2.drawOval((int)(sunX-ringR),(int)(sunY-ringR),(int)(ringR*2),(int)(ringR*2));
                double ringR2=ringR*0.86;
                g2.setStroke(new BasicStroke(Math.max(1f,(float)(sunR*0.1))));
                g2.setColor(new Color(255,130,70,(int)(ringA*0.75)));
                g2.drawOval((int)(sunX-ringR2),(int)(sunY-ringR2),(int)(ringR2*2),(int)(ringR2*2));
                g2.setStroke(new BasicStroke(1));
            }
        }

        void drawBody(Graphics2D g2,Body b){
            // FIX #10: guard against zero streamLen before spaghettification
            if(b.spag&&b.spagP>0.05&&b.radius*(1+b.spagP*9.5)>1){drawSpag(g2,b);return;}
            if(b.type==BT.SHUTTLE){drawShuttle(g2,b);return;}
            drawPlanet(g2,b);
        }

        void drawPlanet(Graphics2D g2,Body b){
            int x=(int)b.x,y=(int)b.y,r=(int)b.radius; if(r<1) return;
            Color[]p=PAL[b.ci%PAL.length];
            if(showTrails&&b.trail.size()>1){
                g2.setStroke(new BasicStroke(Math.max(r*.4f,1f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                for(int i=1;i<b.trail.size();i++){
                    g2.setColor(new Color(p[0].getRed(),p[0].getGreen(),p[0].getBlue(),(int)((float)i/b.trail.size()*62)));
                    Point2D.Double p0=b.trail.get(i-1),p1=b.trail.get(i);
                    g2.drawLine((int)p0.x,(int)p0.y,(int)p1.x,(int)p1.y);
                }
                g2.setStroke(new BasicStroke(1));
            }
            if(b.rings){
                g2.setColor(new Color(p[0].getRed(),p[0].getGreen(),p[0].getBlue(),78)); g2.setStroke(new BasicStroke(r*.35f));
                g2.drawOval(x-(int)(r*1.9),y-(int)(r*1.9*b.ringTilt),(int)(r*3.8),(int)(r*3.8*b.ringTilt));
                g2.setColor(new Color(p[1].getRed(),p[1].getGreen(),p[1].getBlue(),38)); g2.setStroke(new BasicStroke(r*.18f));
                g2.drawOval(x-(int)(r*2.4),y-(int)(r*2.4*b.ringTilt),(int)(r*4.8),(int)(r*4.8*b.ringTilt));
                g2.setStroke(new BasicStroke(1));
            }
            RadialGradientPaint sp=new RadialGradientPaint(x-r*.3f,y-r*.3f,r*1.05f,new float[]{0f,.15f,.65f,1f},
                new Color[]{new Color(255,255,255,220),p[0],p[1],p[2]});
            g2.setPaint(sp); g2.fillOval(x-r,y-r,r*2,r*2);
            RadialGradientPaint rim=new RadialGradientPaint(x,y,r,new float[]{.55f,1f},new Color[]{new Color(0,0,0,0),new Color(0,0,0,140)});
            g2.setPaint(rim); g2.fillOval(x-r,y-r,r*2,r*2);
            for(int m=0;m<b.moons;m++){
                int mx=(int)(b.x+Math.cos(b.mAngle[m])*b.mDist[m]),my=(int)(b.y+Math.sin(b.mAngle[m])*b.mDist[m]);
                int mr=Math.max((int)(r*.22),2); g2.setColor(new Color(152,150,142,195)); g2.fillOval(mx-mr,my-mr,mr*2,mr*2);
            }
            if(!b.name.isEmpty()){
                g2.setColor(new Color(230,240,255,165)); g2.setFont(new Font("Segoe UI",Font.BOLD,12));
                g2.drawString(b.name,x-g2.getFontMetrics().stringWidth(b.name)/2,y-r-4);
            }
        }

        void drawShuttle(Graphics2D g2,Body b){
            int x=(int)b.x,y=(int)b.y,len=Math.max((int)(b.radius*2.5),14),hw=Math.max((int)(b.radius*.72),4);
            Graphics2D gs=(Graphics2D)g2.create();
            gs.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            gs.translate(x,y); gs.rotate(b.shuttleAngle);
            if(b.thrustMain){int fl=(int)(len*.7+Math.random()*6);gs.setPaint(new GradientPaint(-len/2f,0f,new Color(255,242,165,235),(float)(-len/2-fl),0f,new Color(255,90,22,0)));Path2D.Double f=new Path2D.Double();f.moveTo(-len/2.0-1,0);f.lineTo(-len/2.0-fl,-hw*.42);f.lineTo(-len/2.0-fl,hw*.42);f.closePath();gs.fill(f);}
            if(b.thrustReverse){int fl=(int)(len*.45+Math.random()*4);gs.setPaint(new GradientPaint(len/2f,0f,new Color(170,220,255,210),(float)(len/2+fl),0f,new Color(70,140,255,0)));Path2D.Double f=new Path2D.Double();f.moveTo(len/2.0+1,0);f.lineTo(len/2.0+fl,-hw*.34);f.lineTo(len/2.0+fl,hw*.34);f.closePath();gs.fill(f);}
            if(b.thrustLeft){int sl=(int)(hw*1.8+Math.random()*4);gs.setPaint(new GradientPaint(0f,hw+1f,new Color(255,210,130,215),0f,(float)(hw+1+sl),new Color(255,90,20,0)));Path2D.Double f=new Path2D.Double();f.moveTo(0,hw+1);f.lineTo(-3,hw+1+sl);f.lineTo(3,hw+1+sl);f.closePath();gs.fill(f);}
            if(b.thrustRight){int sl=(int)(hw*1.8+Math.random()*4);gs.setPaint(new GradientPaint(0f,-hw-1f,new Color(255,210,130,215),0f,(float)(-hw-1-sl),new Color(255,90,20,0)));Path2D.Double f=new Path2D.Double();f.moveTo(0,-hw-1);f.lineTo(-3,-hw-1-sl);f.lineTo(3,-hw-1-sl);f.closePath();gs.fill(f);}
            gs.setColor(new Color(178,186,204,218)); gs.fillRoundRect(-len/2,-hw,len,hw*2,hw,hw);
            int[]nx={len/2,len/2-6,len/2-6};int[]ny={0,-hw,hw};gs.setColor(new Color(228,234,246));gs.fillPolygon(nx,ny,3);
            gs.setColor(new Color(118,128,152,215));gs.fillRect(-len/3,-hw-2,len/3,2);gs.fillRect(-len/3,hw,len/3,2);
            RadialGradientPaint can=new RadialGradientPaint((float)(len*.08),-hw*.2f,hw*1.1f,new float[]{0f,1f},new Color[]{new Color(190,230,255,215),new Color(70,110,170,115)});
            gs.setPaint(can);gs.fillOval(-1,-hw/2,hw+3,hw);
            gs.setColor(new Color(65,75,98));gs.drawRoundRect(-len/2,-hw,len,hw*2,hw,hw);
            if(b==shuttle){gs.setColor(new Color(100,225,255,148));gs.setStroke(new BasicStroke(1.2f));gs.drawOval(-len/2-5,-hw-5,len+10,hw*2+10);gs.setStroke(new BasicStroke(1));}
            gs.dispose();
        }

        void drawSpag(Graphics2D g2,Body b){
            double pp=b.spagP,ang=b.spagA,t=System.currentTimeMillis()*.001;
            Color[]p=PAL[b.ci%PAL.length];
            double squish=Math.max(.025,1-pp*.975),halfW=b.radius*squish;
            double streamLen=Math.max(1,b.radius*(1+pp*9.5));
            double coreR=b.radius*Math.max(.04,1-pp*.93);
            Graphics2D gs=(Graphics2D)g2.create();
            gs.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            gs.translate(b.x,b.y); gs.rotate(ang);
            int sl=(int)streamLen;
            // outer glow
            if(pp>.08){GradientPaint gp=new GradientPaint(0,0,new Color(p[0].getRed(),p[0].getGreen(),p[0].getBlue(),(int)(pp*.1*255)),sl,0,new Color(0,0,0,0));gs.setPaint(gp);gs.fillOval(-(int)(halfW*2),-(int)(halfW*2),(int)(sl+halfW*4),(int)(halfW*4));}
            // ribbon
            Path2D.Double ribbon=new Path2D.Double(); boolean first=true;
            for(int i=0;i<=60;i++){double frac=(double)i/60,w=halfW*Math.pow(1-frac*frac,.55),wave=w*.2*Math.sin(frac*Math.PI*5+t*4)*pp,px_=frac*streamLen;if(first){ribbon.moveTo(px_,w+wave);first=false;}else ribbon.lineTo(px_,w+wave);}
            for(int i=60;i>=0;i--){double frac=(double)i/60,w=halfW*Math.pow(1-frac*frac,.55);ribbon.lineTo(frac*streamLen,-w+w*.2*Math.sin(frac*Math.PI*5+t*4)*pp);}
            ribbon.closePath();
            // FIX #10: only use LinearGradientPaint if sl>0
            if(sl>0){
                gs.setPaint(new LinearGradientPaint(0,0,sl,0,new float[]{0f,.5f,1f},new Color[]{new Color(p[0].getRed(),p[0].getGreen(),p[0].getBlue(),(int)(.9*255)),new Color(p[1].getRed(),p[1].getGreen(),p[1].getBlue(),(int)(.45*255)),new Color(p[2].getRed(),p[2].getGreen(),p[2].getBlue(),(int)(.03*255))}));
                gs.fill(ribbon);
                gs.setPaint(new LinearGradientPaint(0,0,sl,0,new float[]{0f,.3f,1f},new Color[]{new Color(255,250,210,(int)(pp*.9*255)),new Color(255,200,100,(int)(pp*.55*255)),new Color(p[0].getRed(),p[0].getGreen(),p[0].getBlue(),0)}));
                gs.setStroke(new BasicStroke(Math.max((float)(halfW*.3*(1-pp*.5)),.5f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                gs.drawLine(0,0,sl,0); gs.setStroke(new BasicStroke(1));
            }
            if(coreR>.7){RadialGradientPaint cp=new RadialGradientPaint(0,0,(float)coreR,new float[]{0f,.35f,1f},new Color[]{new Color(255,255,255,(int)(215*(1-pp*.85))),p[0],p[2]});gs.setPaint(cp);gs.fillOval((int)-coreR,(int)-coreR,(int)(coreR*2),(int)(coreR*2));}
            gs.dispose();
        }

        void drawBH(Graphics2D g2,Body b){
            double bx=b.x,by=b.y; int r=(int)b.radius;
            double rot=b.bhRot,t=System.currentTimeMillis()*.001;
            // lensing shadow
            RadialGradientPaint lens=new RadialGradientPaint((float)bx,(float)by,r*10f,new float[]{0f,.12f,.34f,.62f,1f},
                new Color[]{new Color(0,0,0,255),new Color(6,0,20,235),new Color(18,0,55,120),new Color(60,0,120,30),new Color(0,0,0,0)});
            g2.setPaint(lens);g2.fillOval((int)(bx-r*10),(int)(by-r*10),(int)(r*20),(int)(r*20));
            // Einstein ring arcs
            Graphics2D gl=(Graphics2D)g2.create(); gl.translate(bx,by); gl.rotate(rot*.65);
            for(int i=0;i<4;i++){float w=(float)(r*(2.05+i*.2));float h=w*.42f;int al=Math.max(12,34-i*6);gl.setColor(new Color(245,215,175,al));gl.setStroke(new BasicStroke(Math.max(r*(.045f+i*.01f),1f)));gl.drawArc((int)-w,(int)-h,(int)(w*2),(int)(h*2),192,156);}
            gl.dispose();
            // accretion disk
            Graphics2D gd=(Graphics2D)g2.create();
            gd.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            gd.translate(bx,by); gd.rotate(rot); float tilt=.12f; int dl=12;
            for(int layer=dl;layer>=0;layer--){
                float frac=(float)layer/dl,dw=(float)(r*(1.28+frac*2.85)),dh=dw*tilt;
                float flicker=(float)(.8+.2*Math.sin(t*2.9+layer*.43)); int ba=(int)(210*(1-frac*.64));
                Shape oc=gd.getClip(); gd.clipRect((int)-dw,(int)-dh,(int)(dw*2),(int)dh);
                gd.setColor(new Color(Math.min(255,(int)(255*flicker)),Math.min(255,(int)((132+frac*78)*flicker)),Math.min(255,(int)((58+frac*95)*flicker)),(int)(ba*.6)));
                gd.setStroke(new BasicStroke(Math.max(dh*.42f,1f))); gd.drawOval((int)-dw,(int)-dh,(int)(dw*2),(int)(dh*2)); gd.setClip(oc);
                oc=gd.getClip(); gd.clipRect((int)-dw,0,(int)(dw*2),(int)dh);
                gd.setColor(new Color(Math.min(255,(int)(255*flicker)),Math.min(255,(int)((180+frac*55)*flicker)),Math.min(255,(int)((72+frac*92)*flicker)),ba));
                gd.setStroke(new BasicStroke(Math.max(dh*.44f,1f))); gd.drawOval((int)-dw,(int)-dh,(int)(dw*2),(int)(dh*2));
                gd.setColor(new Color(255,248,215,(int)(165*(1-frac*.5)))); gd.setStroke(new BasicStroke(Math.max(dh*.2f,1f))); gd.drawArc((int)-dw,(int)-dh,(int)(dw*2),(int)(dh*2),288,64); gd.setClip(oc);
            }
            float ib=(float)(.84+.16*Math.sin(t*5.5)),iw=(float)(r*1.3f),ih=iw*tilt;
            gd.setColor(new Color(255,(int)(232*ib),(int)(142*ib),240)); gd.setStroke(new BasicStroke(Math.max(ih*.44f,1f))); gd.drawOval((int)-iw,(int)-ih,(int)(iw*2),(int)(ih*2)); gd.dispose();
            // event horizon
            g2.setColor(Color.BLACK); g2.fillOval((int)(bx-r),(int)(by-r),r*2,r*2);
            float pr=r*1.08f; g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(255,210,125,(int)(175*(.72+.28*Math.sin(t*2.1)))));
            g2.drawOval((int)(bx-pr),(int)(by-pr),(int)(pr*2),(int)(pr*2)); g2.setStroke(new BasicStroke(1));
            // jets
            double ja=rot+Math.PI/2;
            for(int dir=0;dir<2;dir++){double jAng=ja+(dir==0?0:Math.PI);float jl=(float)(r*4.8+r*1.4*Math.sin(t*.4));
                LinearGradientPaint jet=new LinearGradientPaint((float)bx,(float)by,(float)(bx+Math.cos(jAng)*jl),(float)(by+Math.sin(jAng)*jl),new float[]{0f,.35f,1f},new Color[]{new Color(180,130,255,70),new Color(110,60,200,26),new Color(70,0,180,0)});
                g2.setPaint(jet); g2.setStroke(new BasicStroke(Math.max(r*.2f,1f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g2.drawLine((int)bx,(int)by,(int)(bx+Math.cos(jAng)*jl),(int)(by+Math.sin(jAng)*jl));}
            g2.setStroke(new BasicStroke(1));
            g2.setColor(new Color(180,100,255,165)); g2.setFont(new Font("Segoe UI",Font.BOLD,12));
            String lbl="BH  M:"+(int)(b.mass/100)+"e2  R:"+(int)b.radius;
            g2.drawString(lbl,(int)(bx-g2.getFontMetrics().stringWidth(lbl)/2),(int)(by-r-9));
        }

        void drawWH(Graphics2D g2,Body b){
            int x=(int)b.x,y=(int)b.y,r=(int)b.radius;
            float pulse=(float)(.7+.3*Math.sin(System.currentTimeMillis()*.002+b.whPulse));
            Color c1=b.whPartner!=null?new Color(0,220,255):new Color(100,100,155);
            RadialGradientPaint glow=new RadialGradientPaint(x,y,r*3.5f,new float[]{0f,.4f,1f},new Color[]{new Color(0,180,255,(int)(48*pulse)),new Color(0,100,200,(int)(16*pulse)),new Color(0,0,0,0)});
            g2.setPaint(glow);g2.fillOval(x-r*3,y-r*3,r*6,r*6);
            g2.setStroke(new BasicStroke(1.5f));
            for(int ring=3;ring>=1;ring--){int rr=(int)(r*(.5+ring*.35));g2.setColor(new Color(c1.getRed(),c1.getGreen(),c1.getBlue(),(int)(75*(1-ring*.2)*pulse)));g2.drawOval(x-rr,y-rr,rr*2,rr*2);}
            g2.setColor(new Color(0,0,20));g2.fillOval(x-(int)(r*.6),y-(int)(r*.6),(int)(r*1.2),(int)(r*1.2));
            g2.setStroke(new BasicStroke(2f));g2.setColor(new Color(c1.getRed(),c1.getGreen(),c1.getBlue(),(int)(190*pulse)));
            g2.drawOval(x-(int)(r*.6),y-(int)(r*.6),(int)(r*1.2),(int)(r*1.2));
            if(b.whPartner!=null&&b.x<b.whPartner.x){g2.setStroke(new BasicStroke(1f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,1f,new float[]{4f,9f},0f));g2.setColor(new Color(0,200,255,20));g2.drawLine(x,y,(int)b.whPartner.x,(int)b.whPartner.y);}
            g2.setStroke(new BasicStroke(1));
            g2.setColor(new Color(115,222,255,165));g2.setFont(new Font("Segoe UI",Font.BOLD,11));g2.drawString("WH",x-8,y-r-4);
        }

        void drawPulsar(Graphics2D g2,Body b){
            int x=(int)b.x,y=(int)b.y,r=(int)b.radius;
            double pulse=(Math.sin(b.pulsarPhase*2*Math.PI/b.pulsarPeriod)+1)/2.0;
            if(showTrails&&b.trail.size()>1){
                g2.setStroke(new BasicStroke(Math.max(r*.35f,1f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                for(int i=1;i<b.trail.size();i++){g2.setColor(new Color(180,160,255,(int)((float)i/b.trail.size()*52)));Point2D.Double p0=b.trail.get(i-1),p1=b.trail.get(i);g2.drawLine((int)p0.x,(int)p0.y,(int)p1.x,(int)p1.y);}
                g2.setStroke(new BasicStroke(1));
            }
            float ha=(float)(.1+pulse*.52);
            RadialGradientPaint halo=new RadialGradientPaint(x,y,r*(3f+(float)pulse*3.5f),new float[]{0f,.45f,1f},new Color[]{new Color(160,210,255,(int)(ha*255)),new Color(100,160,255,(int)(ha*90)),new Color(0,0,0,0)});
            g2.setPaint(halo);g2.fillOval((int)(x-r*6.5),(int)(y-r*6.5),(int)(r*13),(int)(r*13));
            if(pulse>.52){float ja=(float)((pulse-.52)/.48*.8);double jl=r*9*pulse;
                for(int dir=0;dir<2;dir++){double ang=b.pulsarPhase*.7+dir*Math.PI;
                    LinearGradientPaint jet=new LinearGradientPaint(x,y,(float)(x+Math.cos(ang)*jl),(float)(y+Math.sin(ang)*jl),new float[]{0f,.5f,1f},new Color[]{new Color(195,235,255,(int)(ja*255)),new Color(130,175,255,(int)(ja*.45*255)),new Color(100,150,255,0)});
                    g2.setPaint(jet);g2.setStroke(new BasicStroke(r*.62f*(float)pulse,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g2.drawLine(x,y,(int)(x+Math.cos(ang)*jl),(int)(y+Math.sin(ang)*jl));}
                g2.setStroke(new BasicStroke(1));}
            RadialGradientPaint body=new RadialGradientPaint(x-r*.3f,y-r*.3f,r*1.1f,new float[]{0f,.3f,.7f,1f},new Color[]{new Color(225,245,255),new Color(160,205,255),new Color(80,120,225),new Color(20,40,120)});
            g2.setPaint(body);g2.fillOval(x-r,y-r,r*2,r*2);
            g2.setColor(new Color(198,232,255,182));g2.setFont(new Font("Segoe UI",Font.BOLD,11));g2.drawString("PULSAR",x-18,y-r-4);
        }

        // ── close-up ──────────────────────────────────────────────────────────
        void drawCloseup(Graphics2D g2,int pw,int ph,Body b){
            float al=(float)Math.max(0,Math.min(1,cuBlend));
            double cx=cuCX(b),cy=cuCY(b),r=cuR(b);
            // dim background
            g2.setColor(new Color(0,0,0,(int)(175*al))); g2.fillRect(0,0,pw,ph);
            // faint bg stars
            for(int i=0;i<80;i++){
                double sx=((i*173)%pw+Math.sin(i*6.7+frame*.01)*12+pw)%pw;
                double sy=((i*97)%ph+Math.cos(i*4.3+frame*.012)*10+ph)%ph;
                int sa=(int)(al*(20+12*Math.sin(i+frame*.02))); if(sa<1)continue;
                g2.setColor(new Color(200,220,255,sa)); int ss=(i%4==0)?2:1; g2.fillOval((int)sx,(int)sy,ss,ss);
            }
            Color[]p=PAL[b.ci%PAL.length];
            // atmosphere glow
            RadialGradientPaint atmoGlow=new RadialGradientPaint((float)cx,(float)cy,(float)(r*1.4),new float[]{0f,.55f,1f},
                new Color[]{new Color(p[0].getRed(),p[0].getGreen(),p[0].getBlue(),58),new Color(p[1].getRed(),p[1].getGreen(),p[1].getBlue(),28),new Color(0,0,0,0)});
            g2.setPaint(atmoGlow);g2.fillOval((int)(cx-r*1.4),(int)(cy-r*1.4),(int)(r*2.8),(int)(r*2.8));
            // planet sphere
            Ellipse2D sphere=new Ellipse2D.Double(cx-r,cy-r,r*2,r*2);
            Shape oldClip=g2.getClip(); g2.setClip(sphere);
            BufferedImage tex=getTex(b);
            drawSphere(g2,tex,cx,cy,r,cuSpin);
            // atmosphere edge
            RadialGradientPaint atmoEdge=new RadialGradientPaint((float)cx,(float)cy,(float)(r*1.1),new float[]{.76f,.94f,1f},
                new Color[]{new Color(110,170,255,0),new Color(120,185,255,40),new Color(200,230,255,55)});
            g2.setPaint(atmoEdge); g2.fill(sphere);
            // terminator shadow
            g2.setPaint(new LinearGradientPaint((float)(cx-r*.95),(float)(cy-r*.2),(float)(cx+r*.95),(float)(cy+r*.2),
                new float[]{0f,.35f,.6f,1f},new Color[]{new Color(0,0,0,160),new Color(0,0,0,52),new Color(0,0,0,0),new Color(0,0,0,92)}));
            g2.fill(sphere);
            // specular
            g2.setPaint(new RadialGradientPaint((float)(cx-r*.45),(float)(cy-r*.5),(float)(r*.55),new float[]{0f,1f},new Color[]{new Color(255,255,255,115),new Color(255,255,255,0)}));
            g2.fill(sphere);
            g2.setClip(oldClip);
            // sphere outline
            g2.setStroke(new BasicStroke((float)Math.max(1.2,r*.02)));
            g2.setColor(new Color(180,220,255,(int)(105*al))); g2.draw(sphere); g2.setStroke(new BasicStroke(1));

            // planet name
            g2.setColor(new Color(255,255,255,(int)(225*al)));
            g2.setFont(new Font("Segoe UI",Font.BOLD,19));
            String nm=b.name==null||b.name.isEmpty()?b.type.toString():b.name;
            g2.drawString(nm+"  •  Hyperreal Closeup",(int)(pw*.06),(int)(ph*.1));
            g2.setFont(new Font("Segoe UI",Font.PLAIN,13));
            g2.setColor(new Color(200,215,235,(int)(200*al)));
            g2.drawString("Click outside planet to return",(int)(pw*.06),(int)(ph*.1+22));

            // telemetry panel
            drawTelemetry(g2,b,pw,ph,al);
        }

        void drawTelemetry(Graphics2D g2,Body b,int pw,int ph,float al){
            // derive stats
            double mE=b.mass/26.5,rE=Math.max(.1,b.radius/8.0);
            double grav=Math.max(.05,mE/(Math.max(.08,rE*rE)));
            double water=Math.max(0,Math.min(1,.1+Math.abs(Math.sin(b.ci*.9+b.radius*.11))*.7));
            double atm=Math.max(0,grav*42+water*30);
            double alb=.16+.46*Math.max(0,Math.min(1,.4+Math.abs(Math.cos(b.radius*.23+b.ci))));
            double tempK=130+102*Math.max(.35,Math.sqrt(Math.max(8,b.orbitDist))/12.0)+58*alb;
            double dens=5.51*mE/Math.max(.06,rE*rE*rE);
            // calibrate known planets
            String n=b.name==null?"":b.name.toLowerCase();
            if(n.contains("earth")){mE=1;rE=1;grav=1;water=.71;atm=101.3;tempK=288;dens=5.51;}
            else if(n.contains("mars")){mE=.107;rE=.532;grav=.379;water=.01;atm=.6;tempK=210;dens=3.93;}
            else if(n.contains("venus")){mE=.815;rE=.949;grav=.904;water=0;atm=9200;tempK=737;dens=5.24;}
            else if(n.contains("mercury")){mE=.055;rE=.383;grav=.378;water=0;atm=0;tempK=440;dens=5.43;}
            else if(n.contains("jupiter")){mE=317.8;rE=11.21;grav=2.53;water=0;atm=1000;tempK=165;dens=1.33;}
            else if(n.contains("saturn")){mE=95.16;rE=9.45;grav=1.07;water=0;atm=140;tempK=134;dens=.69;}
            else if(n.contains("uranus")){mE=14.54;rE=4.01;grav=.89;water=0;atm=120;tempK=76;dens=1.27;}
            else if(n.contains("neptune")){mE=17.15;rE=3.88;grav=1.14;water=0;atm=110;tempK=72;dens=1.64;}
            else if(n.contains("moon")){mE=.0123;rE=.273;grav=.165;water=0;atm=0;tempK=220;dens=3.34;}
            double tempC=tempK-273.15;
            boolean gas=n.contains("jupiter")||n.contains("saturn")||n.contains("uranus")||n.contains("neptune");
            double pr=Math.max(.0001,atm/101.3);
            double openP=Math.max(0,Math.min(1,(1-Math.abs(tempK-288)/75.0)*.38+(1-Math.abs(Math.log(pr))/1.9)*.34+(1-Math.abs(grav-1)/.65)*.18+(water/.65)*.1));
            if(atm<35||atm>220) openP*=.12; if(tempC<-55||tempC>55) openP*=.25; if(gas) openP=0;
            double habP=Math.max(0,Math.min(1,(1-Math.abs(tempK-294)/260.0)*.34+(1-Math.abs(Math.log(pr))/6.0)*.22+(1-Math.abs(grav-1)/2.6)*.28+(water*.75+(gas?.08:.25))*.16));
            habP*=(gas?.38:1.0); if(tempK>650||tempK<40) habP*=.45; habP=Math.max(0,Math.min(1,habP));
            if(n.contains("earth")){openP=Math.max(.99,openP);habP=Math.max(.995,habP);}

            int px=(int)(pw*.58),py=(int)(ph*.12),panW=(int)(pw*.34),panH=(int)(ph*.52);
            // panel bg
            g2.setPaint(new GradientPaint(px,py,new Color(8,15,32,(int)(210*al)),px,py+panH,new Color(5,10,22,(int)(218*al))));
            g2.fillRoundRect(px,py,panW,panH,18,18);
            g2.setColor(new Color(120,170,235,(int)(125*al))); g2.drawRoundRect(px,py,panW,panH,18,18);

            int tx=px+16,ty=py+28;
            g2.setFont(new Font("Segoe UI",Font.BOLD,16)); g2.setColor(new Color(228,240,255,(int)(238*al))); g2.drawString("Planetary Telemetry",tx,ty);
            g2.setFont(new Font("Segoe UI",Font.PLAIN,13)); g2.setColor(new Color(190,210,242,(int)(225*al)));
            String[]lines={String.format("Type: %s",b.type),String.format("Mass: %.3f M⊕",mE),String.format("Radius: %.3f R⊕",rE),
                String.format("Gravity: %.2f g",grav),String.format("Water: %.1f%%",water*100),
                String.format("Atmosphere: %.1f kPa",atm),String.format("Avg Temp: %.1f°C",tempC),
                String.format("Density: %.2f g/cm³",dens),String.format("Open-air: %d%% (%s)",(int)(openP*100),openP>.72?"High":openP>.45?"Moderate":"Low"),
                String.format("Habitat: %d%% (%s)",(int)(habP*100),habP>.72?"High":habP>.45?"Moderate":"Low")};
            ty+=22; for(String ln:lines){g2.drawString(ln,tx,ty);ty+=19;}

            // survival bars
            int bx2=tx+78,bw=panW-110,bh=8;
            int by0=py+panH-44,by1=py+panH-22;
            g2.setFont(new Font("Segoe UI",Font.PLAIN,11)); g2.setColor(new Color(185,206,238,(int)(205*al)));
            g2.drawString("Open air",tx,by0+7); g2.drawString("Habitat",tx,by1+7);
            g2.setColor(new Color(22,40,72,(int)(195*al)));
            g2.fillRoundRect(bx2,by0,bw,bh,8,8); g2.fillRoundRect(bx2,by1,bw,bh,8,8);
            g2.setPaint(new GradientPaint(bx2,by0,new Color(255,92,80),bx2+bw,by0,new Color(255,208,100)));
            g2.fillRoundRect(bx2,by0,(int)(bw*openP),bh,8,8);
            g2.setPaint(new GradientPaint(bx2,by1,new Color(82,168,255),bx2+bw,by1,new Color(108,252,185)));
            g2.fillRoundRect(bx2,by1,(int)(bw*habP),bh,8,8);
            g2.setColor(new Color(205,225,255,(int)(218*al)));
            g2.drawRoundRect(bx2,by0,bw,bh,8,8); g2.drawRoundRect(bx2,by1,bw,bh,8,8);
        }

        void drawArrow(Graphics2D g2){
            if(cuBlend>.05||cuTarget>.5||drag0==null||drag1==null) return;
            int dx=drag1.x-drag0.x,dy=drag1.y-drag0.y; if(Math.hypot(dx,dy)<5) return;
            g2.setColor(new Color(255,255,255,85));
            g2.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,1f,new float[]{5f,5f},0f));
            g2.drawLine(drag0.x,drag0.y,drag1.x,drag1.y); g2.setStroke(new BasicStroke(1));
            double ang=Math.atan2(dy,dx);
            int[]hx={drag1.x,(int)(drag1.x-12*Math.cos(ang-.4)),(int)(drag1.x-12*Math.cos(ang+.4))};
            int[]hy={drag1.y,(int)(drag1.y-12*Math.sin(ang-.4)),(int)(drag1.y-12*Math.sin(ang+.4))};
            g2.setColor(new Color(255,255,255,145)); g2.fillPolygon(hx,hy,3); g2.fillOval(drag0.x-4,drag0.y-4,8,8);
        }

        void drawHUD(Graphics2D g2,int pw,int ph){
            String th=theme==1?"HAIL MARY":theme==2?"INTERSTELLAR":"DEFAULT";
            String shut=shuttle!=null?"WASD Shuttle Active":"Spawn Shuttle + WASD to fly";
            String star=supernovaActive?"  [SUPERNOVA]":(sunAlive<0.5?"  [SUN: "+(int)(sunAlive*100)+"%]":"");
            String s="Drag=Velocity  |  Click=Closeup  |  Bodies:"+bodies.size()+"  |  "+shut+"  |  Theme:"+th+star+(paused?"  [PAUSED]":"");
            g2.setFont(new Font("Segoe UI",Font.BOLD,13));
            FontMetrics fm=g2.getFontMetrics(); int padX=12,boxH=fm.getHeight()+14;
            int boxY=ph-boxH-8,boxW=Math.min(pw-14,fm.stringWidth(s)+padX*2);
            g2.setColor(new Color(6,10,22,162)); g2.fillRoundRect(7,boxY,boxW,boxH,12,12);
            g2.setColor(new Color(130,162,220,105)); g2.drawRoundRect(7,boxY,boxW,boxH,12,12);
            g2.setColor(new Color(196,216,248,220)); g2.drawString(s,7+padX,boxY+fm.getAscent()+7);
        }
    }
}