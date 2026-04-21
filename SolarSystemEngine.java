import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

public class SolarSystemEngine extends JFrame {

    static final int W = 1150, H = 740;
    static final int CTRL_W = 290;
    static final double G = 0.5;
    static final int MAX_TRAIL = 150;

    static final Color UI_PANEL_BG = new Color(9, 15, 32);
    static final Color UI_PANEL_EDGE = new Color(52, 72, 120);
    static final Color UI_ROW_BG = new Color(18, 28, 52);
    static final Color UI_ROW_EDGE = new Color(45, 63, 102);
    static final Color UI_CARD_TOP = new Color(34, 50, 86, 225);
    static final Color UI_CARD_BOTTOM = new Color(18, 30, 56, 225);
    static final Color UI_CARD_EDGE = new Color(122, 164, 234, 105);
    static final Color UI_ACCENT = new Color(105, 186, 255);
    static final Color UI_TEXT = new Color(228, 236, 252);
    static final Color UI_MUTED = new Color(162, 179, 214);
    static final Font UI_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font UI_FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    static final Font UI_FONT_TITLE = new Font("Segoe UI", Font.BOLD, 15);
    static final Font UI_FONT_HERO = new Font("Segoe UI", Font.BOLD, 20);
    static final Font UI_FONT_SUBTITLE = new Font("Segoe UI", Font.PLAIN, 12);

    static final Color[][] PAL = {
        {new Color(85,147,219),new Color(37,110,200),new Color(12,60,130)},
        {new Color(220,110,80),new Color(200,70,40),new Color(130,40,15)},
        {new Color(80,200,160),new Color(29,158,117),new Color(8,80,55)},
        {new Color(240,195,80),new Color(220,150,30),new Color(140,90,10)},
        {new Color(220,120,170),new Color(200,80,130),new Color(110,30,70)},
        {new Color(170,168,160),new Color(130,128,122),new Color(70,68,65)},
        {new Color(120,200,80),new Color(90,160,40),new Color(50,100,15)},
        {new Color(160,80,220),new Color(120,40,180),new Color(70,15,120)},
    };

    SimPanel sim;
    JLabel statusLabel;
    JSlider sunMassSlider, sunRadSlider, speedSlider, pSizeSlider, eccSlider;
    JComboBox<String> bodyTypeCombo, colorCombo, themeCombo;

    public SolarSystemEngine() {
        setTitle("Solar System Physics Engine");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.BLACK);
        JPanel ctrl = buildControlPanel();
        sim = new SimPanel(this);
        add(sim, BorderLayout.CENTER);
        add(ctrl, BorderLayout.EAST);
        setSize(W, H);
        setLocationRelativeTo(null);
        setVisible(true);
        new Timer(200, e -> {
            ((Timer)e.getSource()).stop();
            sim.createSun();
            sim.loadSolarSystem();
        }).start();
        new Timer(60, e -> updateStatus()).start();
    }

    JPanel buildControlPanel() {
        JPanel p = new JPanel(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint bg=new GradientPaint(0,0,new Color(8,14,30),0,getHeight(),new Color(12,26,50));
                g2.setPaint(bg);g2.fillRect(0,0,getWidth(),getHeight());
                g2.setColor(new Color(120,170,255,24));
                g2.fillOval(-95,-70,getWidth()+180,170);
                g2.setColor(new Color(86,126,215,18));
                g2.fillOval(-60,getHeight()-145,getWidth()+120,220);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(CTRL_W-2, H));
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(14, 12, 14, 12));

        JLabel heroTitle = new JLabel("ORBITAL COMMAND");
        heroTitle.setFont(UI_FONT_HERO);
        heroTitle.setForeground(new Color(234, 243, 255));
        heroTitle.setAlignmentX(CENTER_ALIGNMENT);
        JLabel heroSub = new JLabel("Premium Physics Control Deck");
        heroSub.setFont(UI_FONT_SUBTITLE);
        heroSub.setForeground(new Color(174, 198, 238));
        heroSub.setAlignmentX(CENTER_ALIGNMENT);
        p.add(heroTitle);
        p.add(Box.createVerticalStrut(2));
        p.add(heroSub);
        p.add(Box.createVerticalStrut(12));

        JPanel sunCard = sectionCard("SUN");
        sunMassSlider = slider(200, 8000, 2000, 50);
        sunRadSlider  = slider(8, 80, 28, 1);
        sunCard.add(labeledSlider("Mass", sunMassSlider));
        sunCard.add(Box.createVerticalStrut(6));
        sunCard.add(labeledSlider("Radius", sunRadSlider));
        sunMassSlider.addChangeListener(e -> sim.recomputeOrbits());
        sunRadSlider.addChangeListener(e -> {
            int r = sunRadSlider.getValue();
            int m = Math.max(200, Math.min(8000, (int)(r * r * r * 0.09)));
            sunMassSlider.setValue(m);
            sim.recomputeOrbits();
        });
        p.add(sunCard);

        p.add(Box.createVerticalStrut(10));
        JPanel spawnCard = sectionCard("SPAWN BODY");

        String[] types = { "Planet","Moon","Space Shuttle","Black Hole","Pulsar","Wormhole","Asteroid Belt" };
        bodyTypeCombo = new JComboBox<>(types);
        styleCombo(bodyTypeCombo);
        spawnCard.add(wrapLabel("Type", bodyTypeCombo));
        spawnCard.add(Box.createVerticalStrut(6));

        String[] cols = {"Blue","Coral","Teal","Amber","Pink","Gray","Green","Purple"};
        colorCombo = new JComboBox<>(cols);
        styleCombo(colorCombo);
        spawnCard.add(wrapLabel("Color", colorCombo));

        pSizeSlider = slider(3, 30, 8, 1);
        eccSlider   = slider(0, 85, 0, 1);
        spawnCard.add(Box.createVerticalStrut(6));
        spawnCard.add(labeledSlider("Size", pSizeSlider));
        spawnCard.add(Box.createVerticalStrut(6));
        spawnCard.add(labeledSlider("Ecc x100", eccSlider));

        spawnCard.add(Box.createVerticalStrut(8));
        spawnCard.add(btn("Load Solar System",  e -> sim.loadSolarSystem()));
        spawnCard.add(Box.createVerticalStrut(5));
        spawnCard.add(btn("Add Random Orbiter", e -> sim.addRandomOrbiter()));
        spawnCard.add(Box.createVerticalStrut(5));
        spawnCard.add(btn("Scatter Asteroids",  e -> sim.scatterAsteroids()));
        spawnCard.add(Box.createVerticalStrut(5));
        spawnCard.add(btn("Big Bang",           e -> sim.bigBang()));
        spawnCard.add(Box.createVerticalStrut(5));
        spawnCard.add(btn("Clear All",          e -> sim.clearAll(), new Color(84, 28, 28)));
        p.add(spawnCard);

        p.add(Box.createVerticalStrut(10));
        JPanel displayCard = sectionCard("DISPLAY");
        displayCard.add(toggleBtn("Orbits",   true,  b -> sim.showOrbits = b));
        displayCard.add(Box.createVerticalStrut(5));
        displayCard.add(toggleBtn("Trails",   true,  b -> sim.showTrails = b));
        displayCard.add(Box.createVerticalStrut(5));
        displayCard.add(toggleBtn("Pause",    false, b -> sim.paused = b));
        String[] themes = {"Default","Hail Mary","Interstellar"};
        themeCombo = new JComboBox<>(themes);
        styleCombo(themeCombo);
        themeCombo.addActionListener(e -> { if(sim!=null) sim.setTheme(themeCombo.getSelectedIndex()); });
        displayCard.add(Box.createVerticalStrut(6));
        displayCard.add(wrapLabel("Theme", themeCombo));
        p.add(displayCard);

        p.add(Box.createVerticalStrut(10));
        JPanel simCard = sectionCard("SIMULATION");
        speedSlider = slider(1, 50, 10, 1);
        simCard.add(labeledSlider("Speed x10", speedSlider));
        p.add(simCard);

        p.add(Box.createVerticalGlue());
        JPanel statusWrap = sectionCard("STATUS");
        statusLabel = new JLabel("Bodies: 0");
        statusLabel.setForeground(new Color(190, 210, 244));
        statusLabel.setFont(UI_FONT_BOLD);
        statusLabel.setAlignmentX(CENTER_ALIGNMENT);
        statusWrap.add(statusLabel);
        p.add(statusWrap);

        JScrollPane scroll = new JScrollPane(p);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.setBackground(UI_PANEL_BG);

        JPanel shell = new JPanel(new BorderLayout());
        shell.setBackground(UI_PANEL_BG);
        shell.setPreferredSize(new Dimension(CTRL_W, H));
        shell.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, UI_PANEL_EDGE));
        shell.add(scroll, BorderLayout.CENTER);
        return shell;
    }

    void updateStatus() {
        long bh = sim.bodies.stream().filter(b -> b.type == BodyType.BLACK_HOLE).count();
        long wh = sim.bodies.stream().filter(b -> b.type == BodyType.WORMHOLE).count();
        long sh = sim.bodies.stream().filter(b -> b.type == BodyType.SHUTTLE).count();
        statusLabel.setText("<html><div style='text-align:center;'>Bodies <b>" + sim.bodies.size()
            + "</b><br>BH " + bh + "  |  WH " + wh + "  |  Shuttle " + sh + "</div></html>");
    }

    JPanel sectionCard(String title){
        JPanel card = new JPanel(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                RoundRectangle2D box = new RoundRectangle2D.Double(0.5,0.5,getWidth()-1.0,getHeight()-1.0,16,16);
                GradientPaint gp = new GradientPaint(0,0,UI_CARD_TOP,0,getHeight(),UI_CARD_BOTTOM);
                g2.setPaint(gp);
                g2.fill(box);
                g2.setColor(UI_CARD_EDGE);
                g2.draw(box);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(CENTER_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1200));
        card.setBorder(BorderFactory.createEmptyBorder(9, 10, 10, 10));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(new Color(165, 208, 255));
        titleLabel.setFont(UI_FONT_TITLE);
        titleLabel.setAlignmentX(CENTER_ALIGNMENT);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        card.add(titleLabel);

        return card;
    }

    JLabel sectionLabel(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(new Color(142, 194, 255)); l.setFont(UI_FONT_TITLE);
        l.setAlignmentX(CENTER_ALIGNMENT); l.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
        return l;
    }
    JSlider slider(int mn, int mx, int v, int step) {
        JSlider s = new JSlider(mn,mx,v);
        s.setBackground(UI_ROW_BG); s.setForeground(UI_ACCENT);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE,34));
        return s;
    }
    JPanel labeledSlider(String lbl, JSlider s) {
        JPanel row = new JPanel(new BorderLayout(8,0)); row.setBackground(UI_ROW_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));
        JLabel l = new JLabel(lbl); l.setForeground(UI_MUTED);
        l.setFont(UI_FONT); l.setPreferredSize(new Dimension(98,22));
        JLabel v = new JLabel(String.valueOf(s.getValue()));
        v.setForeground(new Color(255,218,146)); v.setFont(UI_FONT_BOLD);
        v.setPreferredSize(new Dimension(54,22)); v.setHorizontalAlignment(SwingConstants.RIGHT);
        s.addChangeListener(e -> v.setText(String.valueOf(s.getValue())));
        row.add(l,BorderLayout.WEST); row.add(s,BorderLayout.CENTER); row.add(v,BorderLayout.EAST);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UI_ROW_EDGE, 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        return row;
    }
    JPanel wrapLabel(String lbl, JComponent c) {
        JPanel row = new JPanel(new BorderLayout(8,0)); row.setBackground(UI_ROW_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE,40));
        JLabel l = new JLabel(lbl); l.setForeground(UI_MUTED);
        l.setFont(UI_FONT); l.setPreferredSize(new Dimension(98,22));
        row.add(l,BorderLayout.WEST); row.add(c,BorderLayout.CENTER);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UI_ROW_EDGE, 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        return row;
    }
    void styleCombo(JComboBox<?> c) {
        c.setBackground(new Color(28,44,78)); c.setForeground(UI_TEXT);
        c.setFont(UI_FONT_BOLD); c.setMaximumSize(new Dimension(Integer.MAX_VALUE,36));
        c.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        c.setRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean isSelected,boolean cellHasFocus){
                JLabel lbl=(JLabel)super.getListCellRendererComponent(list,value,index,isSelected,cellHasFocus);
                lbl.setFont(UI_FONT_BOLD);
                lbl.setBorder(BorderFactory.createEmptyBorder(5,8,5,8));
                if(isSelected){
                    lbl.setBackground(new Color(54, 94, 154));
                    lbl.setForeground(new Color(240, 246, 255));
                } else {
                    lbl.setBackground(new Color(28,44,78));
                    lbl.setForeground(UI_TEXT);
                }
                return lbl;
            }
        });
    }
    JButton btn(String text, ActionListener al) { return btn(text,al,new Color(30, 46, 82)); }
    JButton btn(String text, ActionListener al, Color bg) {
        JButton b = new JButton(text){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color base=bg;
                if(!isEnabled()) base=new Color(58,68,92);
                else if(getModel().isPressed()) base=bg.darker();
                else if(getModel().isRollover()) base=bg.brighter();
                GradientPaint gp=new GradientPaint(0,0,base.brighter(),0,getHeight(),base.darker());
                g2.setPaint(gp);
                g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,14,14);
                g2.setColor(new Color(135,168,228,118));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,14,14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setForeground(UI_TEXT);
        b.setFont(UI_FONT_BOLD);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(8,12,8,12));
        b.setFocusPainted(false); b.setMaximumSize(new Dimension(Integer.MAX_VALUE,40));
        b.setAlignmentX(CENTER_ALIGNMENT); b.addActionListener(al);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setRolloverEnabled(true);
        return b;
    }
    JToggleButton toggleBtn(String text, boolean def, java.util.function.Consumer<Boolean> cb) {
        JToggleButton b = new JToggleButton(text,def){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color base=isSelected()?new Color(56, 108, 166):new Color(34, 53, 90);
                if(getModel().isPressed()) base=base.darker();
                else if(getModel().isRollover()) base=base.brighter();
                GradientPaint gp=new GradientPaint(0,0,base.brighter(),0,getHeight(),base.darker());
                g2.setPaint(gp);
                g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                g2.setColor(new Color(130,162,225,112));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(UI_FONT_BOLD);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(7,10,7,10));
        b.setFocusPainted(false); b.setMaximumSize(new Dimension(Integer.MAX_VALUE,38));
        b.setAlignmentX(CENTER_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setRolloverEnabled(true);
        Runnable syncTheme = () -> b.setForeground(b.isSelected()?new Color(241,246,255):new Color(193,208,235));
        syncTheme.run();
        b.addActionListener(e -> {
            syncTheme.run();
            cb.accept(b.isSelected());
        });
        return b;
    }

    public static void main(String[] args){
        SwingUtilities.invokeLater(() -> new SolarSystemEngine());
    }
}
