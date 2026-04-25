import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Launcher extends JFrame {

    static final int W = 1100, H = 700;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Launcher::new);
    }

    public Launcher() {
        setTitle("Physics Lab — Mission Select");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(W, H);
        setLocationRelativeTo(null);
        setResizable(true);
        setContentPane(new HomePanel(this));
        setVisible(true);
    }

    void openSolarSim() {
        setVisible(false);
        dispose();
        SwingUtilities.invokeLater(() -> new SolarSystemEngine(() -> SwingUtilities.invokeLater(Launcher::new)));
    }

    void openCollisionSim() {
        setVisible(false);
        dispose();
        SwingUtilities.invokeLater(() -> new CollisionEngine(() -> SwingUtilities.invokeLater(Launcher::new)));
    }

    void openBuoyancySim() {
        setVisible(false);
        dispose();
        SwingUtilities.invokeLater(() -> new BuoyancyEngine(() -> SwingUtilities.invokeLater(Launcher::new)));
    }

    // ════════════════════════════════════════════════════════════════════════
    static class HomePanel extends JPanel {

        final Launcher launcher;

        // stars
        final float[] sx, sy, sz, spd;
        static final int STAR_COUNT = 320;

        // orbiting decorative particles
        static class Orb {
            double angle, radius, size, speed, alpha;
            int colorIdx;
        }
        final List<Orb> orbs = new ArrayList<>();

        float pulse = 0f;
        float hoverSpace = 0f, hoverCollide = 0f;
        boolean mouseOnSpace = false, mouseOnCollide = false;

        javax.swing.Timer animTimer;
        long startTime = System.currentTimeMillis();

        static final Color C_BG1  = new Color(2, 3, 10);
        static final Color C_BG2  = new Color(5, 8, 22);
        static final Color C_MUTED = new Color(120, 145, 190);

        static final int CARD_W = 300, CARD_H = 380, GAP = 60;
        int spaceCardX, collideCardX, cardY;

        HomePanel(Launcher launcher) {
            this.launcher = launcher;
            setBackground(C_BG1);

            Random rng = new Random(42);
            sx = new float[STAR_COUNT]; sy = new float[STAR_COUNT];
            sz = new float[STAR_COUNT]; spd = new float[STAR_COUNT];
            for (int i = 0; i < STAR_COUNT; i++) {
                sx[i] = rng.nextFloat();
                sy[i] = rng.nextFloat();
                sz[i] = rng.nextFloat() * 2.2f + 0.3f;
                spd[i] = rng.nextFloat() * 0.15f + 0.02f;
            }

            for (int i = 0; i < 28; i++) {
                Orb o = new Orb();
                o.angle    = rng.nextDouble() * Math.PI * 2;
                o.radius   = 200 + rng.nextDouble() * 240;
                o.size     = rng.nextDouble() * 3.5 + 1;
                o.speed    = (rng.nextDouble() * 0.008 + 0.002) * (rng.nextBoolean() ? 1 : -1);
                o.alpha    = 0.2 + rng.nextDouble() * 0.55;
                o.colorIdx = rng.nextInt(3);
                orbs.add(o);
            }

            updateLayout();

            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    updateLayout();
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseMoved(MouseEvent e) {
                    mouseOnSpace   = inCard(e.getX(), e.getY(), spaceCardX);
                    mouseOnCollide = inCard(e.getX(), e.getY(), collideCardX);
                    setCursor(Cursor.getPredefinedCursor(
                        (mouseOnSpace || mouseOnCollide) ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }
            });
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (inCard(e.getX(), e.getY(), spaceCardX))   launcher.openSolarSim();
                    if (inCard(e.getX(), e.getY(), collideCardX)) showCollisionBuoyancyMenu();
                }
                public void mouseExited(MouseEvent e) { mouseOnSpace = mouseOnCollide = false; }
            });

            animTimer = new javax.swing.Timer(16, e -> {
                pulse = (float)((System.currentTimeMillis() - startTime) * 0.001);
                for (Orb o : orbs) o.angle += o.speed;
                hoverSpace   += (mouseOnSpace   ? 1 : -1) * 0.08f;
                hoverSpace    = Math.max(0f, Math.min(1f, hoverSpace));
                hoverCollide += (mouseOnCollide ? 1 : -1) * 0.08f;
                hoverCollide  = Math.max(0f, Math.min(1f, hoverCollide));
                repaint();
            });
            animTimer.start();
        }

        void updateLayout() {
            int pw = getWidth() > 0 ? getWidth() : W;
            int ph = getHeight() > 0 ? getHeight() : H;
            spaceCardX = pw / 2 - GAP / 2 - CARD_W;
            collideCardX = pw / 2 + GAP / 2;
            cardY = ph / 2 - CARD_H / 2 + 30;
        }

        boolean inCard(int mx, int my, int cx) {
            return mx >= cx && mx <= cx + CARD_W && my >= cardY && my <= cardY + CARD_H;
        }

        void showComingSoon() {
            JDialog d = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), "Coming Soon", true);
            JPanel dp = new JPanel(new BorderLayout());
            dp.setBackground(new Color(8, 12, 30));
            dp.setBorder(BorderFactory.createLineBorder(new Color(60, 80, 160), 1));
            JLabel lbl = new JLabel("<html><div style='text-align:center;padding:24px 32px;'>"
                + "<span style='font-size:18px;color:#a0c4ff'>Collisions & Buoyancy</span><br><br>"
                + "<span style='color:#7090c0'>This module is under development.<br>Stay tuned for the next update!</span>"
                + "</div></html>", SwingConstants.CENTER);
            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            JButton ok = new JButton("Got it");
            ok.setBackground(new Color(25, 50, 110));
            ok.setForeground(new Color(160, 200, 255));
            ok.setFont(new Font("Segoe UI", Font.BOLD, 13));
            ok.setFocusPainted(false);
            ok.setBorder(BorderFactory.createEmptyBorder(8, 28, 8, 28));
            ok.addActionListener(ev -> d.dispose());
            JPanel btnPanel = new JPanel(); btnPanel.setBackground(new Color(8,12,30));
            btnPanel.setBorder(BorderFactory.createEmptyBorder(0,0,16,0));
            btnPanel.add(ok);
            dp.add(lbl, BorderLayout.CENTER);
            dp.add(btnPanel, BorderLayout.SOUTH);
            d.setContentPane(dp); d.setSize(380, 200); d.setLocationRelativeTo(this); d.setVisible(true);
        }

        void showCollisionBuoyancyMenu() {
            JDialog d = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), "Choose Simulation", true);
            JPanel dp = new JPanel(new BorderLayout(10, 10));
            dp.setBackground(new Color(8, 12, 30));
            dp.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 80, 160), 1),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)
            ));

            JLabel lbl = new JLabel("Launch Collision or Buoyancy module", SwingConstants.CENTER);
            lbl.setForeground(new Color(180, 210, 255));
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));

            JButton collisionBtn = new JButton("Collision Engine");
            JButton buoyancyBtn = new JButton("Buoyancy Engine");
            JButton cancelBtn = new JButton("Cancel");

            JButton[] buttons = {collisionBtn, buoyancyBtn, cancelBtn};
            for (JButton b : buttons) {
                b.setFocusPainted(false);
                b.setFont(new Font("Segoe UI", Font.BOLD, 12));
                b.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
            }
            collisionBtn.setBackground(new Color(25, 65, 130));
            collisionBtn.setForeground(new Color(175, 215, 255));
            buoyancyBtn.setBackground(new Color(20, 72, 126));
            buoyancyBtn.setForeground(new Color(175, 225, 255));
            cancelBtn.setBackground(new Color(30, 38, 65));
            cancelBtn.setForeground(new Color(170, 185, 215));

            collisionBtn.addActionListener(ev -> {
                d.dispose();
                launcher.openCollisionSim();
            });
            buoyancyBtn.addActionListener(ev -> {
                d.dispose();
                launcher.openBuoyancySim();
            });
            cancelBtn.addActionListener(ev -> d.dispose());

            JPanel btnPanel = new JPanel(new GridLayout(1, 3, 10, 0));
            btnPanel.setOpaque(false);
            btnPanel.add(collisionBtn);
            btnPanel.add(buoyancyBtn);
            btnPanel.add(cancelBtn);

            dp.add(lbl, BorderLayout.NORTH);
            dp.add(btnPanel, BorderLayout.CENTER);

            d.setContentPane(dp);
            d.setSize(560, 150);
            d.setLocationRelativeTo(this);
            d.setVisible(true);
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            int pw = getWidth();
            int ph = getHeight();
            updateLayout();
            drawBg(g2, pw, ph);
            drawStars(g2, pw, ph);
            drawOrbs(g2, pw, ph);
            drawHeader(g2, pw, ph);
            drawCard(g2, spaceCardX,   cardY, true,  hoverSpace);
            drawCard(g2, collideCardX, cardY, false, hoverCollide);
            drawFooter(g2, pw, ph);
        }

        void drawBg(Graphics2D g2, int pw, int ph) {
            g2.setPaint(new GradientPaint(pw/2f,0,C_BG2,pw/2f,ph,C_BG1)); g2.fillRect(0,0,pw,ph);
            float br = (float)(280 + 20*Math.sin(pulse*0.4));
            RadialGradientPaint bloom = new RadialGradientPaint(pw/2f,ph/2f,br,new float[]{0f,.45f,1f},
                new Color[]{new Color(20,45,100,22),new Color(10,25,60,12),new Color(0,0,0,0)});
            g2.setPaint(bloom); g2.fillOval(pw/2-(int)br,ph/2-(int)br,(int)(br*2),(int)(br*2));
            // subtle grid
            g2.setStroke(new BasicStroke(0.4f)); g2.setColor(new Color(30,55,110,16));
            for(int x=0;x<pw;x+=55) g2.drawLine(x,0,x,ph);
            for(int y=0;y<ph;y+=55) g2.drawLine(0,y,pw,y);
            g2.setStroke(new BasicStroke(1));
            // edge accents
            g2.setPaint(new GradientPaint(pw*.15f,0,new Color(60,140,255,0),pw*.5f,0,new Color(60,140,255,20)));
            g2.fillRect(0,0,pw,2);
            g2.setPaint(new GradientPaint(pw*.15f,ph,new Color(60,140,255,0),pw*.5f,ph,new Color(60,140,255,16)));
            g2.fillRect(0,ph-2,pw,2);
        }

        void drawStars(Graphics2D g2, int pw, int ph) {
            for(int i=0;i<STAR_COUNT;i++){
                float tw=.5f+.5f*(float)Math.sin(pulse*spd[i]*6+i);
                float alpha=Math.max(.05f,Math.min(1f,tw*.85f));
                int r=Math.max((int)(sz[i]*1.4f),1);
                int x=(int)(sx[i]*pw);
                int y=(int)(sy[i]*ph);
                if(i%22==0){
                    g2.setColor(new Color(200,220,255,(int)(alpha*55))); g2.fillOval(x-4,y-4,8,8);
                    g2.setColor(new Color(255,255,255,(int)(alpha*38)));
                    g2.drawLine(x-6,y,x+6,y);
                    g2.drawLine(x,y-6,x,y+6);
                }
                g2.setColor(new Color(1f,1f,1f,alpha));
                g2.fillOval(x,y,r,r);
            }
        }

        void drawOrbs(Graphics2D g2, int pw, int ph) {
            double cx=pw/2.0,cy=ph/2.0;
            Color[]oc={new Color(80,160,255),new Color(255,180,60),new Color(120,220,160)};
            for(Orb o:orbs){
                double ox=cx+Math.cos(o.angle)*o.radius,oy=cy+Math.sin(o.angle)*o.radius*.38;
                Color base=oc[o.colorIdx];
                g2.setColor(new Color(base.getRed(),base.getGreen(),base.getBlue(),(int)(o.alpha*75)));
                g2.fillOval((int)(ox-o.size),(int)(oy-o.size),(int)(o.size*2),(int)(o.size*2));
            }
        }

        void drawHeader(Graphics2D g2, int pw, int ph) {
            g2.setFont(new Font("Segoe UI",Font.BOLD,12)); g2.setColor(new Color(90,150,220,185));
            String ey="✦  PHYSICS SIMULATION LAB  ✦"; FontMetrics fm=g2.getFontMetrics();
            g2.drawString(ey,pw/2-fm.stringWidth(ey)/2,62);

            g2.setFont(new Font("Segoe UI",Font.BOLD,52));
            String title="MISSION SELECT"; fm=g2.getFontMetrics(); int tx=pw/2-fm.stringWidth(title)/2;
            g2.setColor(new Color(30,80,200,38)); g2.drawString(title,tx+2,127);
            g2.setColor(new Color(235,243,255)); g2.drawString(title,tx,125);

            g2.setFont(new Font("Segoe UI",Font.PLAIN,16)); g2.setColor(C_MUTED);
            String sub="Choose your simulation environment to begin"; fm=g2.getFontMetrics();
            g2.drawString(sub,pw/2-fm.stringWidth(sub)/2,156);

            g2.setStroke(new BasicStroke(.8f));
            g2.setPaint(new GradientPaint(pw*.18f,172,new Color(60,120,255,0),pw*.5f,172,new Color(60,120,255,55)));
            g2.drawLine((int)(pw*.18),(int)172,(int)(pw*.82),(int)172);
            g2.setStroke(new BasicStroke(1));
        }

        void drawCard(Graphics2D g2,int cx,int cy,boolean isSpace,float hover){
            float lift=hover*10f; int x=cx,y=(int)(cy-lift),w=CARD_W,h=CARD_H;
            // shadow
            g2.setColor(new Color(0,5,20,(int)(18+hover*32))); g2.fillRoundRect(x+4,y+8,w,h,22,22);
            // bg
            Color ct=new Color(12,22,55,228);
            Color cb=new Color(8,14,38,238);
            g2.setPaint(new GradientPaint(x,y,ct,x,y+h,cb)); g2.fillRoundRect(x,y,w,h,22,22);
            // edge glow
            Color e0=new Color(55,110,220,65);
            Color e1=new Color(90,160,255,155);
            g2.setColor(blend(e0,e1,hover)); g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(x,y,w,h,22,22); g2.setStroke(new BasicStroke(1));
            // top accent bar
            Color bar=new Color(70,150,255);
            g2.setColor(blend(new Color(bar.getRed(),bar.getGreen(),bar.getBlue(),55),new Color(bar.getRed(),bar.getGreen(),bar.getBlue(),135),hover));
            g2.fillRoundRect(x+20,y+14,w-40,4,4,4);
            // icon
            drawIcon(g2,x+w/2,y+90,isSpace,hover,pulse);
            // title
            String t1=isSpace?"Solar System":"Collisions &",t2=isSpace?"Physics Engine":"Buoyancy";
            g2.setFont(new Font("Segoe UI",Font.BOLD,22)); FontMetrics fm=g2.getFontMetrics();
            g2.setColor(new Color(228,238,255));
            g2.drawString(t1,x+w/2-fm.stringWidth(t1)/2,y+185);
            g2.setColor(blend(new Color(180,200,240),new Color(220,230,255),hover));
            g2.drawString(t2,x+w/2-fm.stringWidth(t2)/2,y+210);
            // description lines
            String[]desc=isSpace?new String[]{"Gravity · Orbits · Kepler","Black Holes · Spaghettification","Wormholes · Pulsars · Shuttle"}:new String[]{"Rigid body collisions","Buoyancy & fluid dynamics","Coming soon — stay tuned!"};
            g2.setFont(new Font("Segoe UI",Font.PLAIN,13)); fm=g2.getFontMetrics(); g2.setColor(C_MUTED);
            for(int i=0;i<desc.length;i++) g2.drawString(desc[i],x+w/2-fm.stringWidth(desc[i])/2,y+240+i*21);
            // divider
            g2.setColor(new Color(60,90,160,48)); g2.drawLine(x+25,y+300,x+w-25,y+300);
            // button
            drawBtn(g2,x+w/2,y+338,isSpace,hover);
            // status badge
            String badge=isSpace?"● AVAILABLE":"◌ COMING SOON";
            Color bc=isSpace?new Color(80,200,120):new Color(140,120,180);
            g2.setFont(new Font("Segoe UI",Font.BOLD,11)); fm=g2.getFontMetrics();
            g2.setColor(blend(new Color(bc.getRed(),bc.getGreen(),bc.getBlue(),110),new Color(bc.getRed(),bc.getGreen(),bc.getBlue(),215),hover));
            g2.drawString(badge,x+w/2-fm.stringWidth(badge)/2,y+h-14);
        }

        void drawIcon(Graphics2D g2,int cx,int cy,boolean isSpace,float hover,float t){
            if(isSpace){
                RadialGradientPaint sg=new RadialGradientPaint(cx,cy,32,new float[]{0f,.5f,1f},new Color[]{new Color(255,230,100,55),new Color(255,160,30,18),new Color(0,0,0,0)});
                g2.setPaint(sg); g2.fillOval(cx-32,cy-32,64,64);
                RadialGradientPaint sun=new RadialGradientPaint(cx-4,cy-4,18,new float[]{0f,.5f,1f},new Color[]{new Color(255,252,200),new Color(255,190,50),new Color(200,90,10)});
                g2.setPaint(sun); g2.fillOval(cx-18,cy-18,36,36);
                g2.setStroke(new BasicStroke(.6f));
                int[]orR={36,52,66}; Color[]orC={new Color(255,160,60),new Color(80,180,255),new Color(200,120,80)}; double[]spds={1.4,.9,.6};
                for(int i=0;i<3;i++){
                    g2.setColor(new Color(60,100,180,26)); g2.drawOval(cx-orR[i],cy-orR[i],orR[i]*2,orR[i]*2);
                    double angle=t*spds[i]+i*2.1;
                    int px=(int)(cx+Math.cos(angle)*orR[i]),py=(int)(cy+Math.sin(angle)*orR[i]);
                    int pr=(i==1)?5:4;
                    RadialGradientPaint pl=new RadialGradientPaint(px-1,py-1,pr,new float[]{0f,1f},new Color[]{new Color(orC[i].getRed(),orC[i].getGreen(),orC[i].getBlue(),238),new Color(orC[i].getRed()/2,orC[i].getGreen()/2,orC[i].getBlue()/2,198)});
                    g2.setPaint(pl); g2.fillOval(px-pr,py-pr,pr*2,pr*2);
                }
                g2.setStroke(new BasicStroke(1));
            } else {
                double bounce=Math.sin(t*2.2)*.5+.5; int gap2=(int)(bounce*28);
                RadialGradientPaint s1=new RadialGradientPaint(cx-20-gap2/2-3,cy-3,22,new float[]{0f,.5f,1f},new Color[]{new Color(180,210,255,238),new Color(80,140,220,198),new Color(30,70,150,178)});
                g2.setPaint(s1); g2.fillOval(cx-40-gap2/2,cy-22,44,44);
                RadialGradientPaint s2=new RadialGradientPaint(cx+20+gap2/2-3,cy-3,22,new float[]{0f,.5f,1f},new Color[]{new Color(255,200,160,238),new Color(220,130,60,198),new Color(150,60,20,178)});
                g2.setPaint(s2); g2.fillOval(cx+gap2/2,cy-22,44,44);
                g2.setColor(new Color(80,160,220,22));
                for(int r=12;r<50;r+=12) g2.drawOval(cx-r,cy+24,r*2,(int)(r*.35));
            }
        }

        void drawBtn(Graphics2D g2,int cx,int cy,boolean isSpace,float hover){
            int bw=180,bh=40,bx=cx-bw/2,by=cy-bh/2;
            Color base=new Color(35,75,165);
            Color hov =new Color(55,115,235);
            Color col=blend(base,hov,hover);
            g2.setPaint(new GradientPaint(bx,by,col.brighter(),bx,by+bh,col)); g2.fillRoundRect(bx,by,bw,bh,12,12);
            Color edgeBase=new Color(80,130,220,78);
            Color edgeHov =new Color(100,170,255,175);
            g2.setColor(blend(edgeBase,edgeHov,hover)); g2.setStroke(new BasicStroke(1.2f)); g2.drawRoundRect(bx,by,bw,bh,12,12); g2.setStroke(new BasicStroke(1));
            String label="Launch Simulation →";
            g2.setFont(new Font("Segoe UI",Font.BOLD,14)); FontMetrics fm=g2.getFontMetrics();
            Color tc=new Color(180,215,255);
            Color th=new Color(235,245,255);
            g2.setColor(blend(tc,th,hover)); g2.drawString(label,cx-fm.stringWidth(label)/2,by+bh/2+fm.getAscent()/2-1);
        }

        void drawFooter(Graphics2D g2, int pw, int ph){
            g2.setFont(new Font("Segoe UI",Font.PLAIN,12)); g2.setColor(new Color(65,90,140));
            String f="Physics Lab  ·  Solar System Engine v2  ·  Collisions & Buoyancy coming soon";
            FontMetrics fm=g2.getFontMetrics(); g2.drawString(f,W/2-fm.stringWidth(f)/2,H-18);
        }

        static Color blend(Color a,Color b,float t){
            t=Math.max(0f,Math.min(1f,t));
            return new Color((int)(a.getRed()+(b.getRed()-a.getRed())*t),(int)(a.getGreen()+(b.getGreen()-a.getGreen())*t),(int)(a.getBlue()+(b.getBlue()-a.getBlue())*t),(int)(a.getAlpha()+(b.getAlpha()-a.getAlpha())*t));
        }
    }
}