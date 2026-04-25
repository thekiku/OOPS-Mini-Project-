import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class CollisionEngine extends JFrame {

    // ─── window ───────────────────────────────────────────────────────────────
    static final int W = 1200, H = 800;
    static final int CTRL_W = 310;
    static final int SIM_H = 760;   // simulation canvas height (excl. bottom strip)

    // ─── palette ──────────────────────────────────────────────────────────────
    static final Color BG_DEEP   = new Color(5,  7, 18);
    static final Color BG_MID    = new Color(9, 13, 28);
    static final Color PANEL_BG  = new Color(7, 11, 26);
    static final Color CARD_T    = new Color(16, 26, 54, 228);
    static final Color CARD_B    = new Color(10, 17, 40, 228);
    static final Color CARD_EDGE = new Color(55, 85, 170, 80);
    static final Color TEXT_PRI  = new Color(222, 232, 252);
    static final Color TEXT_MUT  = new Color(140, 160, 205);
    static final Color GRID_COL  = new Color(25, 40, 85, 40);
    static final Color FLOOR_COL = new Color(55, 80, 160);
    static final Color WALL_COL  = new Color(90, 130, 220);

    // box colours
    static final Color[] BOX_COLORS = {
        new Color(220,  65,  55),   // red
        new Color( 55, 185,  80),   // green
        new Color( 55, 135, 230),   // blue
    };
    static final String[] BOX_NAMES = {"RED", "GREEN", "BLUE"};

    // ─── simulation state ─────────────────────────────────────────────────────
    SimPanel sim;
    Runnable onBack;

    // ─── entry ────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(CollisionEngine::new);
    }
    public CollisionEngine() { this(null); }
    public CollisionEngine(Runnable backCb) {
        this.onBack = backCb;
        setTitle("Collision Physics Engine");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_DEEP);
        sim = new SimPanel();
        add(sim, BorderLayout.CENTER);
        add(buildControlPanel(), BorderLayout.EAST);
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle screen = ge.getMaximumWindowBounds();
        setSize(Math.min(W, screen.width), Math.min(H, screen.height));
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  BOX  — physics body
    // ══════════════════════════════════════════════════════════════════════════
    static class Box {
        int id;
        double x, y;          // centre position
        double vx, vy;        // velocity
        double mass;
        double initVel;       // magnitude
        int    initDir;       // 0=right 1=left 2=up 3=down
        boolean active;
        boolean sleeping;     // tiny velocity → frozen
        Color   col;
        // visual
        List<Point2D.Double> trail = new ArrayList<>();
        int trailMax = 80;
        double squishX = 1, squishY = 1;  // collision squash
        double glowPulse = 0;

        Box(int id, Color col) {
            this.id = id; this.col = col;
            mass = 20; initVel = 150; initDir = (id == 0 ? 0 : 1);
            active = (id < 2);
        }

        double side() {
            // box side length: grows with mass but asymptotically
            return 30 + 55 * (1 - 1.0 / (1 + mass / 40.0));
        }

        Rectangle2D.Double rect() {
            double s = side();
            return new Rectangle2D.Double(x - s/2*squishX, y - s/2*squishY,
                                          s*squishX, s*squishY);
        }

        void applyDir() {
            switch (initDir) {
                case 0: vx =  initVel; vy = 0; break;
                case 1: vx = -initVel; vy = 0; break;
                case 2: vx = 0; vy = -initVel; break;
                case 3: vx = 0; vy =  initVel; break;
            }
        }

        void addTrail() {
            trail.add(new Point2D.Double(x, y));
            if (trail.size() > trailMax) trail.remove(0);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PARTICLE  — collision sparks
    // ══════════════════════════════════════════════════════════════════════════
    static class Spark {
        double x, y, vx, vy, life, maxLife;
        Color c;
        float sz;
        Spark(double x, double y, Color c) {
            this.x=x; this.y=y; this.c=c;
            double ang = Math.random()*Math.PI*2;
            double spd = 40+Math.random()*160;
            vx=Math.cos(ang)*spd; vy=Math.sin(ang)*spd;
            maxLife=life=0.4+Math.random()*0.6;
            sz=(float)(2+Math.random()*3);
        }
        boolean tick(double dt) {
            x+=vx*dt; y+=vy*dt; vx*=0.92; vy*=0.92; vy+=80*dt;
            life-=dt; return life<=0;
        }
        void draw(Graphics2D g) {
            float a=(float)(life/maxLife);
            g.setColor(new Color(c.getRed(),c.getGreen(),c.getBlue(),Math.max(0,Math.min(255,(int)(a*210)))));
            int s=Math.max(1,(int)(sz*a));
            g.fillOval((int)(x-s*.5),(int)(y-s*.5),s,s);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SIMULATION PANEL
    // ══════════════════════════════════════════════════════════════════════════
    class SimPanel extends JPanel {
        Box[] boxes;
        List<Spark> sparks = new ArrayList<>();

        boolean wallLeft=false, wallRight=false;
        double elasticity = 0.8;   // 0=perfectly inelastic, 1=perfectly elastic
        boolean running = false;
        boolean gravity = false;
        boolean showVectors = true;
        boolean showTrails = true;
        boolean showEnergy = true;

        double[] stX, stY, stR, stB;   // background stars
        javax.swing.Timer loop;

        // energy display
        double initKE = 0;

        SimPanel() {
            setBackground(BG_DEEP); setOpaque(true);
            Random rng = new Random(7);
            int ns=220; stX=new double[ns];stY=new double[ns];stR=new double[ns];stB=new double[ns];
            for(int i=0;i<ns;i++){stX[i]=rng.nextDouble();stY[i]=rng.nextDouble();stR[i]=rng.nextDouble()*1.3+0.3;stB[i]=rng.nextDouble()*0.5+0.2;}

            boxes = new Box[]{new Box(0,BOX_COLORS[0]), new Box(1,BOX_COLORS[1]), new Box(2,BOX_COLORS[2])};
            resetPositions();

            loop = new javax.swing.Timer(15, e -> {
                double dt = 0.015;
                if (running) tick(dt);
                // animate squish recovery
                for (Box b : boxes) {
                    b.squishX += (1.0 - b.squishX) * 0.18;
                    b.squishY += (1.0 - b.squishY) * 0.18;
                    if (Math.abs(b.squishX-1)<0.001) b.squishX=1;
                    if (Math.abs(b.squishY-1)<0.001) b.squishY=1;
                    if (b.glowPulse > 0) b.glowPulse -= 0.04;
                }
                sparks.removeIf(s -> s.tick(dt));
                repaint();
            });
            loop.start();
        }

        void resetPositions() {
            int pw = getWidth()>10 ? getWidth() : W-CTRL_W;
            int floor = floorY();
            int[] xs = {pw/4, pw/2, 3*pw/4};
            for (int i=0;i<3;i++) {
                Box b = boxes[i];
                b.x = xs[i];
                b.y = floor - b.side()/2;
                b.vx = b.vy = 0;
                b.trail.clear();
                b.squishX = b.squishY = 1;
            }
            running = false; sparks.clear();
        }

        int floorY() { return getHeight()>10 ? getHeight()-60 : H-60; }
        int leftWallX()  { return 30; }
        int rightWallX() { return (getWidth()>10?getWidth():W-CTRL_W) - 30; }

        void launch() {
            resetPositions();
            initKE = 0;
            for (Box b : boxes) {
                if (!b.active) continue;
                b.applyDir();
                initKE += 0.5 * b.mass * (b.vx*b.vx + b.vy*b.vy);
            }
            running = true;
        }

        // ── physics ───────────────────────────────────────────────────────────
        void tick(double dt) {
            int floor = floorY();
            int leftW  = leftWallX();
            int rightW = rightWallX();

            for (Box b : boxes) {
                if (!b.active) continue;
                if (gravity) b.vy += 500 * dt;

                double speed = Math.hypot(b.vx, b.vy);
                if (speed < 0.5 && Math.abs(b.y - (floor - b.side()/2)) < 1) {
                    b.vx = b.vy = 0;
                    b.y = floor - b.side()/2;
                    b.sleeping = true;
                } else {
                    b.sleeping = false;
                }
                if (!b.sleeping) {
                    b.x += b.vx * dt;
                    b.y += b.vy * dt;
                    if (showTrails) b.addTrail();
                }

                // floor
                double half = b.side()/2;
                if (b.y + half > floor) {
                    b.y = floor - half;
                    if (Math.abs(b.vy) > 1) {
                        b.vy = -b.vy * elasticity;
                        b.vx *= (0.7 + 0.3*elasticity);
                        triggerSquish(b, 1.4, 0.7);
                        spawnSparks((int)b.x,(int)(b.y+half),b.col,4);
                    } else { b.vy=0; }
                }
                // ceiling
                if (b.y - half < 0) { b.y = half; b.vy = Math.abs(b.vy)*elasticity; }

                // walls
                if (wallLeft && b.x - half < leftW) {
                    b.x = leftW + half;
                    if (Math.abs(b.vx) > 1) {
                        b.vx = Math.abs(b.vx) * elasticity;
                        triggerSquish(b, 0.7, 1.3);
                        spawnSparks(leftW,(int)b.y,b.col,5);
                    } else { b.vx = 0; }
                }
                if (wallRight && b.x + half > rightW) {
                    b.x = rightW - half;
                    if (Math.abs(b.vx) > 1) {
                        b.vx = -Math.abs(b.vx) * elasticity;
                        triggerSquish(b, 0.7, 1.3);
                        spawnSparks(rightW,(int)b.y,b.col,5);
                    } else { b.vx = 0; }
                }
            }

            // box-box collisions
            Box[] active = Arrays.stream(boxes).filter(b -> b.active).toArray(Box[]::new);
            for (int i=0;i<active.length;i++) {
                for (int j=i+1;j<active.length;j++) {
                    resolveCollision(active[i], active[j]);
                }
            }
        }

        void triggerSquish(Box b, double sx, double sy) {
            b.squishX = sx; b.squishY = sy; b.glowPulse = 1.0;
        }

        void spawnSparks(int x, int y, Color c, int n) {
            for (int i=0;i<n;i++) sparks.add(new Spark(x,y,c));
        }

        void resolveCollision(Box a, Box b) {
            double hs = (a.side() + b.side()) / 2.0;
            double dx = b.x - a.x, dy = b.y - a.y;
            double ox = hs - Math.abs(dx);
            double oy = hs - Math.abs(dy);
            if (ox <= 0 || oy <= 0) return;   // no overlap

            double nx, ny;      // collision normal
            double overlap;
            if (ox < oy) {
                nx = dx < 0 ? -1 : 1; ny = 0; overlap = ox;
            } else {
                nx = 0; ny = dy < 0 ? -1 : 1; overlap = oy;
            }

            // positional correction
            double totalM = a.mass + b.mass;
            a.x -= nx * overlap * (b.mass / totalM);
            a.y -= ny * overlap * (b.mass / totalM);
            b.x += nx * overlap * (a.mass / totalM);
            b.y += ny * overlap * (a.mass / totalM);

            // relative velocity along normal
            double rvn = (b.vx - a.vx)*nx + (b.vy - a.vy)*ny;
            if (rvn > 0) return;  // already separating

            double j = -(1 + elasticity) * rvn / (1.0/a.mass + 1.0/b.mass);

            a.vx -= j/a.mass * nx;
            a.vy -= j/a.mass * ny;
            b.vx += j/b.mass * nx;
            b.vy += j/b.mass * ny;

            // friction
            double friction = 0.15 * (1 - elasticity*0.5);
            double tx = -ny, ty = nx;
            double rvt = (b.vx-a.vx)*tx + (b.vy-a.vy)*ty;
            double jt = -friction * rvt / (1.0/a.mass + 1.0/b.mass);
            a.vx -= jt/a.mass * tx; a.vy -= jt/a.mass * ty;
            b.vx += jt/b.mass * tx; b.vy += jt/b.mass * ty;

            // squish + sparks
            triggerSquish(a, nx==0?1.3:0.7, ny==0?1.3:0.7);
            triggerSquish(b, nx==0?1.3:0.7, ny==0?1.3:0.7);
            int cx=(int)((a.x+b.x)/2), cy=(int)((a.y+b.y)/2);
            spawnSparks(cx,cy,mixColor(a.col,b.col,0.5),12);
            spawnSparks(cx,cy,a.col,4);
            spawnSparks(cx,cy,b.col,4);
        }

        static Color mixColor(Color a, Color b, double t) {
            return new Color(
                (int)(a.getRed()+(b.getRed()-a.getRed())*t),
                (int)(a.getGreen()+(b.getGreen()-a.getGreen())*t),
                (int)(a.getBlue()+(b.getBlue()-a.getBlue())*t));
        }

        // ── paint ─────────────────────────────────────────────────────────────
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int pw = getWidth(), ph = getHeight();
            drawBackground(g2, pw, ph);
            drawStars(g2, pw, ph);
            drawFloor(g2, pw, ph);
            if (wallLeft)  drawWall(g2, leftWallX(), ph, true);
            if (wallRight) drawWall(g2, rightWallX(), ph, false);
            if (showVectors) drawVelocityArrows(g2);
            drawTrails(g2);
            drawBoxes(g2);
            for (Spark s : sparks) s.draw(g2);
            if (showEnergy) drawEnergyHUD(g2, pw, ph);
            drawStatusHUD(g2, pw, ph);
        }

        void drawBackground(Graphics2D g2, int pw, int ph) {
            g2.setPaint(new GradientPaint(pw/2f,0,BG_MID,pw/2f,ph,BG_DEEP));
            g2.fillRect(0,0,pw,ph);
            // grid
            g2.setColor(GRID_COL); g2.setStroke(new BasicStroke(.5f));
            for(int x=0;x<pw;x+=50) g2.drawLine(x,0,x,ph);
            for(int y=0;y<ph;y+=50) g2.drawLine(0,y,pw,y);
            g2.setStroke(new BasicStroke(1));
        }

        void drawStars(Graphics2D g2, int pw, int ph) {
            long t = System.currentTimeMillis();
            for(int i=0;i<stX.length;i++){
                float tw=(float)(stB[i]*(0.5+0.5*Math.sin(t*.0003*stB[i]*3+i)));
                g2.setColor(new Color(1f,1f,1f,Math.max(.04f,Math.min(.7f,tw))));
                int r=Math.max(1,(int)(stR[i]*1.3));
                g2.fillOval((int)(stX[i]*pw),(int)(stY[i]*ph),r,r);
            }
        }

        void drawFloor(Graphics2D g2, int pw, int ph) {
            int fy = floorY();
            // subtle glow
            RadialGradientPaint floorGlow = new RadialGradientPaint(pw/2f, fy, pw*0.7f,
                new float[]{0f,.5f,1f},
                new Color[]{new Color(55,90,200,20),new Color(30,60,160,8),new Color(0,0,0,0)});
            g2.setPaint(floorGlow); g2.fillRect(0, fy-20, pw, 40);
            // line
            g2.setPaint(new GradientPaint(0,fy,new Color(40,65,160,0),pw/2f,fy,new Color(80,120,240,180)));
            g2.setStroke(new BasicStroke(1.5f)); g2.drawLine(0,fy,pw,fy);
            // tick marks
            g2.setColor(new Color(60,90,180,60)); g2.setStroke(new BasicStroke(.8f));
            for(int x=0;x<pw;x+=25) g2.drawLine(x,fy,x,fy+4);
            g2.setStroke(new BasicStroke(1));
            // label
            g2.setFont(new Font("Segoe UI",Font.PLAIN,10));
            g2.setColor(new Color(80,110,190,130));
            g2.drawString("SURFACE",8,fy-5);
        }

        void drawWall(Graphics2D g2, int wx, int ph, boolean isLeft) {
            int fy = floorY();
            // glow
            RadialGradientPaint wg = new RadialGradientPaint(wx, ph/2f, ph/2f,
                new float[]{0f,.4f,1f},
                new Color[]{new Color(80,130,255,28),new Color(50,100,220,10),new Color(0,0,0,0)});
            g2.setPaint(wg); g2.fillRect(isLeft?0:wx-20, 0, 20, ph);
            // line
            g2.setStroke(new BasicStroke(2f));
            g2.setPaint(new GradientPaint(wx,0,new Color(60,100,220,40),wx,fy,new Color(100,160,255,200)));
            g2.drawLine(wx,0,wx,fy);
            // chevrons
            g2.setColor(new Color(80,130,255,70)); g2.setStroke(new BasicStroke(1.2f));
            for(int y=20;y<fy;y+=30){
                int d=isLeft?6:-6;
                g2.drawLine(wx,y,wx+d,y+10); g2.drawLine(wx+d,y+10,wx,y+20);
            }
            g2.setStroke(new BasicStroke(1));
            g2.setFont(new Font("Segoe UI",Font.BOLD,9));
            g2.setColor(new Color(100,150,255,160));
            g2.drawString(isLeft?"WALL":"WALL",wx+(isLeft?4:-22),15);
        }

        void drawVelocityArrows(Graphics2D g2) {
            Box[] toRender = Arrays.stream(boxes).filter(b -> b.active).toArray(Box[]::new);
            // build overlap map on a scratch buffer for color mixing
            // draw arrows per box first in their color, with composite for overlap
            // We use AlphaComposite so overlapping arrows mix via screen blending
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
            for (Box b : toRender) {
                double spd = Math.hypot(b.vx, b.vy);
                if (spd < 0.5 && !running) spd = b.initVel;
                if (spd < 1) continue;

                // arrow direction: use velocity if running, else initDir
                double ax, ay;
                if (running) { ax=b.vx/spd; ay=b.vy/spd; }
                else {
                    switch(b.initDir){
                        case 0: ax=1;ay=0;break; case 1: ax=-1;ay=0;break;
                        case 2: ax=0;ay=-1;break; default: ax=0;ay=1;
                    }
                }
                double arrowLen = 20 + spd * 0.55;
                arrowLen = Math.min(arrowLen, 260);
                double ex = b.x + ax * arrowLen;
                double ey = b.y + ay * arrowLen;

                // determine if this arrow overlaps another
                Color drawCol = b.col;
                for (Box other : toRender) {
                    if (other == b || !other.active) continue;
                    double os = Math.hypot(other.vx, other.vy);
                    if (os < 0.5 && !running) os = other.initVel;
                    if (os < 1) continue;
                    double oLen = Math.min(20+os*0.55,260);
                    // simple check: are arrow bounding boxes near each other?
                    double odx=other.x-b.x, ody=other.y-b.y;
                    if(Math.hypot(odx,ody)<arrowLen+oLen) {
                        // blend arrow colour in overlapping zone
                        drawCol = mixColor(drawCol, other.col, 0.45);
                    }
                }

                float alpha = 0.82f;
                Color stroke1 = new Color(drawCol.getRed(),drawCol.getGreen(),drawCol.getBlue(),(int)(alpha*255));
                // glow pass
                g2.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(drawCol.getRed(),drawCol.getGreen(),drawCol.getBlue(),32));
                g2.drawLine((int)b.x,(int)b.y,(int)ex,(int)ey);
                // main line
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(stroke1);
                g2.drawLine((int)b.x,(int)b.y,(int)ex,(int)ey);
                // arrowhead
                double headLen=12, headAngle=0.45;
                double ang = Math.atan2(ay,ax);
                int[] hx={
                    (int)ex,(int)(ex-headLen*Math.cos(ang-headAngle)),(int)(ex-headLen*Math.cos(ang+headAngle))
                };
                int[] hy={
                    (int)ey,(int)(ey-headLen*Math.sin(ang-headAngle)),(int)(ey-headLen*Math.sin(ang+headAngle))
                };
                g2.setColor(stroke1); g2.fillPolygon(hx,hy,3);
                // speed label
                String spdLabel=String.format("%.0f",spd)+" px/s";
                g2.setFont(new Font("Segoe UI",Font.BOLD,11));
                g2.setColor(new Color(drawCol.getRed(),drawCol.getGreen(),drawCol.getBlue(),200));
                g2.drawString(spdLabel,(int)(ex+ax*6),(int)(ey+ay*6));
            }
            g2.setStroke(new BasicStroke(1));
        }

        void drawTrails(Graphics2D g2) {
            if (!showTrails) return;
            for (Box b : boxes) {
                if (!b.active || b.trail.size() < 2) continue;
                for (int i=1;i<b.trail.size();i++) {
                    float a = (float)i/b.trail.size()*0.35f;
                    g2.setColor(new Color(b.col.getRed(),b.col.getGreen(),b.col.getBlue(),(int)(a*255)));
                    g2.setStroke(new BasicStroke(Math.max(.6f,a*3.5f)));
                    Point2D.Double p0=b.trail.get(i-1),p1=b.trail.get(i);
                    g2.drawLine((int)p0.x,(int)p0.y,(int)p1.x,(int)p1.y);
                }
                g2.setStroke(new BasicStroke(1));
            }
        }

        void drawBoxes(Graphics2D g2) {
            for (Box b : boxes) {
                if (!b.active) continue;
                double s = b.side();
                int bx=(int)(b.x-s/2*b.squishX), by=(int)(b.y-s/2*b.squishY);
                int bw=(int)(s*b.squishX), bh=(int)(s*b.squishY);

                // glow (collision pulse)
                if (b.glowPulse > 0) {
                    float gp=(float)b.glowPulse;
                    RadialGradientPaint glow = new RadialGradientPaint((float)b.x,(float)b.y,(float)(s*1.5),
                        new float[]{0f,.5f,1f},
                        new Color[]{new Color(b.col.getRed(),b.col.getGreen(),b.col.getBlue(),(int)(gp*80)),
                            new Color(b.col.getRed(),b.col.getGreen(),b.col.getBlue(),(int)(gp*30)),
                            new Color(0,0,0,0)});
                    g2.setPaint(glow);
                    g2.fillOval((int)(b.x-s*1.5),(int)(b.y-s*1.5),(int)(s*3),(int)(s*3));
                }

                // shadow
                g2.setColor(new Color(0,0,0,55));
                g2.fillRoundRect(bx+4,by+5,bw,bh,8,8);

                // box fill gradient
                GradientPaint fill = new GradientPaint(bx,by,
                    new Color(Math.min(255,b.col.getRed()+60),Math.min(255,b.col.getGreen()+40),Math.min(255,b.col.getBlue()+40)),
                    bx,by+bh, new Color(b.col.getRed()/2,b.col.getGreen()/2,b.col.getBlue()/2));
                g2.setPaint(fill);
                g2.fillRoundRect(bx,by,bw,bh,8,8);

                // rim highlight (top edge)
                g2.setColor(new Color(255,255,255,55));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawLine(bx+6,by+2,bx+bw-6,by+2);

                // border
                g2.setColor(new Color(b.col.getRed(),b.col.getGreen(),b.col.getBlue(),180));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(bx,by,bw,bh,8,8);
                g2.setStroke(new BasicStroke(1));

                // label: name + mass
                g2.setFont(new Font("Segoe UI",Font.BOLD,11));
                g2.setColor(new Color(255,255,255,200));
                String lbl=BOX_NAMES[b.id];
                FontMetrics fm=g2.getFontMetrics();
                g2.drawString(lbl,(int)(b.x-fm.stringWidth(lbl)/2.0),(int)(b.y+3));
                g2.setFont(new Font("Segoe UI",Font.PLAIN,10));
                g2.setColor(new Color(255,255,255,140));
                String ml=String.format("%.0fkg",(double)b.mass);
                fm=g2.getFontMetrics();
                g2.drawString(ml,(int)(b.x-fm.stringWidth(ml)/2.0),(int)(b.y+15));

                // velocity readout under box when running
                if (running && (Math.abs(b.vx)>1||Math.abs(b.vy)>1)) {
                    g2.setFont(new Font("Segoe UI",Font.PLAIN,10));
                    String vs=String.format("v=%.0f",Math.hypot(b.vx,b.vy));
                    g2.setColor(new Color(b.col.getRed(),b.col.getGreen(),b.col.getBlue(),180));
                    fm=g2.getFontMetrics();
                    g2.drawString(vs,(int)(b.x-fm.stringWidth(vs)/2.0),(int)(b.y+s/2+16));
                }
            }
        }

        void drawEnergyHUD(Graphics2D g2, int pw, int ph) {
            double totalKE = 0;
            for(Box b:boxes) if(b.active) totalKE+=0.5*b.mass*(b.vx*b.vx+b.vy*b.vy);
            double ratio = initKE>0 ? Math.min(1, totalKE/initKE) : 0;

            int hx=12,hy=12,hw=180,hh=54;
            g2.setColor(new Color(8,12,28,185)); g2.fillRoundRect(hx,hy,hw,hh,12,12);
            g2.setColor(new Color(50,80,170,90)); g2.drawRoundRect(hx,hy,hw,hh,12,12);

            g2.setFont(new Font("Segoe UI",Font.BOLD,11));
            g2.setColor(new Color(180,200,240,200));
            g2.drawString("KINETIC ENERGY",hx+10,hy+16);

            // bar
            int bx2=hx+10, by2=hy+24, bw2=hw-20, bh2=8;
            g2.setColor(new Color(20,35,80)); g2.fillRoundRect(bx2,by2,bw2,bh2,6,6);
            Color ec = ratio>0.8?new Color(80,220,120):ratio>0.4?new Color(255,200,60):new Color(220,70,60);
            g2.setPaint(new GradientPaint(bx2,by2,ec.brighter(),bx2+bw2,by2,ec));
            g2.fillRoundRect(bx2,by2,(int)(bw2*ratio),bh2,6,6);
            g2.setColor(new Color(100,130,200,90)); g2.drawRoundRect(bx2,by2,bw2,bh2,6,6);

            g2.setFont(new Font("Segoe UI",Font.PLAIN,10));
            g2.setColor(new Color(160,185,230,180));
            g2.drawString(String.format("KE=%.0f  (%.0f%%)",totalKE,ratio*100),bx2,hy+48);
        }

        void drawStatusHUD(Graphics2D g2, int pw, int ph) {
            String st = running ? "● RUNNING" : "◌ READY";
            Color sc = running ? new Color(80,220,110) : new Color(160,180,220);
            String el = String.format("Elasticity: %.0f%%", elasticity*100);
            String gv = gravity ? "Gravity: ON" : "Gravity: OFF";
            String info = st + "   " + el + "   " + gv;
            g2.setFont(new Font("Segoe UI",Font.BOLD,12));
            FontMetrics fm=g2.getFontMetrics();
            int bw=fm.stringWidth(info)+24, bh=24;
            int bx=pw/2-bw/2, by=ph-34;
            g2.setColor(new Color(7,11,24,175)); g2.fillRoundRect(bx,by,bw,bh,10,10);
            g2.setColor(new Color(45,70,150,80)); g2.drawRoundRect(bx,by,bw,bh,10,10);
            g2.setColor(sc); g2.drawString(st,bx+10,by+bh/2+4);
            g2.setColor(new Color(170,190,230,200));
            g2.drawString("   "+el+"   "+gv, bx+10+fm.stringWidth(st), by+bh/2+4);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CONTROL PANEL
    // ══════════════════════════════════════════════════════════════════════════
    JPanel buildControlPanel() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(PANEL_BG);
        outer.setPreferredSize(new Dimension(CTRL_W, H));
        outer.setBorder(BorderFactory.createMatteBorder(0,1,0,0,new Color(40,65,140)));

        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0,0,new Color(8,12,26),0,getHeight(),new Color(6,10,22)));
                g2.fillRect(0,0,getWidth(),getHeight());
                g2.setColor(new Color(50,90,180,14)); g2.fillOval(-60,-40,getWidth()+120,160);
                g2.setColor(new Color(40,70,160,10)); g2.fillOval(-40,getHeight()-120,getWidth()+80,200);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(12,10,12,10));

        // header
        JLabel hero=new JLabel("COLLISION LAB",SwingConstants.CENTER);
        hero.setFont(new Font("Segoe UI",Font.BOLD,18)); hero.setForeground(new Color(220,232,255));
        hero.setAlignmentX(CENTER_ALIGNMENT);
        JLabel sub=new JLabel("Physics Engine",SwingConstants.CENTER);
        sub.setFont(new Font("Segoe UI",Font.PLAIN,12)); sub.setForeground(new Color(130,155,200));
        sub.setAlignmentX(CENTER_ALIGNMENT);
        p.add(hero); p.add(vgap(2)); p.add(sub); p.add(vgap(10));

        // per-box control cards
        for(int i=0;i<3;i++) p.add(buildBoxCard(i));

        p.add(vgap(8));

        // Global settings card
        JPanel globCard = card("SIMULATION");

        // Elasticity
        globCard.add(sectionLbl("Elasticity"));
        JPanel elRow = new JPanel(new BorderLayout(6,0)); elRow.setBackground(new Color(14,22,48,0));
        elRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JLabel elMin=miniLbl("PLASTIC"); JLabel elMax=miniLbl("ELASTIC");
        JSlider elSlider = makeSlider(0,100,80,null);
        elSlider.addChangeListener(e -> sim.elasticity = elSlider.getValue()/100.0);
        JLabel elVal=valLbl(String.format("%.0f%%",(double)80));
        elSlider.addChangeListener(e -> elVal.setText(String.format("%.0f%%",(double)elSlider.getValue())));
        elRow.add(elMin,BorderLayout.WEST); elRow.add(elSlider,BorderLayout.CENTER); elRow.add(elMax,BorderLayout.EAST);
        globCard.add(elRow); globCard.add(elVal); globCard.add(vgap(4));

        // Gravity toggle
        globCard.add(sectionLbl("Gravity"));
        JToggleButton gravBtn = styleToggle("Gravity OFF", false, new Color(28,46,88));
        gravBtn.addActionListener(e -> {
            sim.gravity = gravBtn.isSelected();
            gravBtn.setText(sim.gravity ? "Gravity ON" : "Gravity OFF");
        });
        globCard.add(gravBtn); globCard.add(vgap(4));

        // Walls
        globCard.add(sectionLbl("Walls"));
        JPanel wallRow = new JPanel(); wallRow.setBackground(new Color(0,0,0,0));
        wallRow.setLayout(new BoxLayout(wallRow,BoxLayout.X_AXIS));
        wallRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,34));
        JToggleButton wLeft  = styleToggle("◀ LEFT",  false, new Color(22,40,82));
        JToggleButton wRight = styleToggle("RIGHT ▶", false, new Color(22,40,82));
        JToggleButton wBoth  = styleToggle("BOTH",    false, new Color(22,40,82));
        wLeft.addActionListener(e -> {
            sim.wallLeft=wLeft.isSelected();
            if(wLeft.isSelected()){wRight.setSelected(false);wBoth.setSelected(false);sim.wallRight=false;}
        });
        wRight.addActionListener(e -> {
            sim.wallRight=wRight.isSelected();
            if(wRight.isSelected()){wLeft.setSelected(false);wBoth.setSelected(false);sim.wallLeft=false;}
        });
        wBoth.addActionListener(e -> {
            boolean b=wBoth.isSelected();
            sim.wallLeft=sim.wallRight=b;
            wLeft.setSelected(false); wRight.setSelected(false);
        });
        wallRow.add(wLeft); wallRow.add(javax.swing.Box.createHorizontalStrut(4));
        wallRow.add(wRight); wallRow.add(javax.swing.Box.createHorizontalStrut(4));
        wallRow.add(wBoth);
        globCard.add(wallRow); globCard.add(vgap(4));

        // Display toggles
        globCard.add(sectionLbl("Display"));
        JPanel dispRow = new JPanel(); dispRow.setBackground(new Color(0,0,0,0));
        dispRow.setLayout(new BoxLayout(dispRow,BoxLayout.X_AXIS));
        dispRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,34));
        JToggleButton tVec   = styleToggle("Arrows", true,  new Color(20,38,78));
        JToggleButton tTrail = styleToggle("Trails", true,  new Color(20,38,78));
        JToggleButton tEnergy= styleToggle("Energy", true,  new Color(20,38,78));
        tVec.addActionListener(e->sim.showVectors=tVec.isSelected());
        tTrail.addActionListener(e->sim.showTrails=tTrail.isSelected());
        tEnergy.addActionListener(e->sim.showEnergy=tEnergy.isSelected());
        dispRow.add(tVec); dispRow.add(javax.swing.Box.createHorizontalStrut(4));
        dispRow.add(tTrail); dispRow.add(javax.swing.Box.createHorizontalStrut(4));
        dispRow.add(tEnergy);
        globCard.add(dispRow);
        p.add(globCard); p.add(vgap(8));

        // action buttons
        JButton launch = bigBtn("▶  LAUNCH", new Color(30,90,50), new Color(80,210,100));
        launch.addActionListener(e -> sim.launch());
        JButton reset = bigBtn("↺  RESET", new Color(22,42,88), new Color(120,160,240));
        reset.addActionListener(e -> { sim.resetPositions(); sim.running=false; });
        p.add(launch); p.add(vgap(5)); p.add(reset);

        if (onBack != null) {
            p.add(vgap(8));
            JButton backBtn = bigBtn("← HOME", new Color(12,22,50), new Color(100,150,220));
            backBtn.addActionListener(e -> {
                setVisible(false); dispose();
                if(onBack!=null) SwingUtilities.invokeLater(onBack);
            });
            p.add(backBtn);
        }

        JScrollPane scroll = new JScrollPane(p);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        scroll.getViewport().setOpaque(false); scroll.setOpaque(false);
        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    JPanel buildBoxCard(int idx) {
        Color bc = BOX_COLORS[idx];
        Box box  = sim.boxes[idx];
        String nm = BOX_NAMES[idx];

        JPanel card = new JPanel(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                RoundRectangle2D box2=new RoundRectangle2D.Double(.5,.5,getWidth()-1,getHeight()-1,16,16);
                g2.setPaint(new GradientPaint(0,0,CARD_T,0,getHeight(),CARD_B)); g2.fill(box2);
                // colored left accent
                g2.setColor(new Color(bc.getRed(),bc.getGreen(),bc.getBlue(),140));
                g2.fillRoundRect(0,0,4,getHeight(),4,4);
                g2.setColor(CARD_EDGE); g2.draw(box2);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(CENTER_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2000));
        card.setBorder(BorderFactory.createEmptyBorder(8,10,10,10));

        // header row: toggle active
        JPanel hdr=new JPanel(new BorderLayout(6,0)); hdr.setBackground(new Color(0,0,0,0));
        hdr.setMaximumSize(new Dimension(Integer.MAX_VALUE,28));
        JLabel title=new JLabel(nm+" BOX");
        title.setFont(new Font("Segoe UI",Font.BOLD,13));
        title.setForeground(bc);
        JToggleButton activeBtn=styleToggle("ON",box.active,bc.darker().darker());
        activeBtn.setFont(new Font("Segoe UI",Font.BOLD,10));
        activeBtn.addActionListener(e -> { box.active=activeBtn.isSelected(); activeBtn.setText(box.active?"ON":"OFF"); });
        hdr.add(title,BorderLayout.WEST); hdr.add(activeBtn,BorderLayout.EAST);
        card.add(hdr); card.add(vgap(6));

        // MASS slider
        card.add(boxSectionLbl("Mass (kg)",bc));
        JSlider massSlider = makeSlider(1,200,(int)box.mass,bc);
        JLabel massVal = valLbl(String.format("%.0f kg",box.mass));
        massSlider.addChangeListener(e -> {
            box.mass = massSlider.getValue();
            massVal.setText(String.format("%.0f kg",box.mass));
            sim.resetPositions();
        });
        card.add(massSlider); card.add(massVal); card.add(vgap(4));

        // INITIAL VELOCITY slider
        card.add(boxSectionLbl("Init. Velocity",bc));
        JSlider velSlider = makeSlider(0,600,(int)box.initVel,bc);
        JLabel velVal = valLbl(String.format("%.0f px/s",box.initVel));
        velSlider.addChangeListener(e -> {
            box.initVel = velSlider.getValue();
            velVal.setText(String.format("%.0f px/s",box.initVel));
        });
        card.add(velSlider); card.add(velVal); card.add(vgap(4));

        // DIRECTION chooser
        card.add(boxSectionLbl("Direction",bc));
        JPanel dirPanel=new JPanel(); dirPanel.setBackground(new Color(0,0,0,0));
        dirPanel.setLayout(new BoxLayout(dirPanel,BoxLayout.X_AXIS));
        dirPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE,32));
        String[] dirNames={"→","←","↑","↓"};
        ButtonGroup dirGroup=new ButtonGroup();
        for(int d=0;d<4;d++){
            final int dir=d;
            JToggleButton db=new JToggleButton(dirNames[d]){
                @Override protected void paintComponent(Graphics g){
                    Graphics2D g2=(Graphics2D)g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    Color base=isSelected()?new Color(bc.getRed(),bc.getGreen(),bc.getBlue(),160):new Color(18,28,55);
                    g2.setPaint(new GradientPaint(0,0,base.brighter(),0,getHeight(),base));
                    g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,8,8);
                    if(isSelected()){g2.setColor(new Color(bc.getRed(),bc.getGreen(),bc.getBlue(),180));g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,8,8);}
                    g2.dispose();super.paintComponent(g);
                }
            };
            db.setFont(new Font("Segoe UI",Font.BOLD,14));
            db.setForeground(box.initDir==d?bc:new Color(140,160,200));
            db.setContentAreaFilled(false);db.setOpaque(false);
            db.setBorder(BorderFactory.createEmptyBorder(3,6,3,6));
            db.setFocusPainted(false);
            db.setSelected(box.initDir==d);
            db.addActionListener(e->{ box.initDir=dir; db.setForeground(bc); });
            db.addItemListener(e->db.setForeground(db.isSelected()?bc:new Color(140,160,200)));
            dirGroup.add(db); dirPanel.add(db);
            if(d<3) dirPanel.add(javax.swing.Box.createHorizontalStrut(3));
        }
        card.add(dirPanel); card.add(vgap(4));

        // ACCELERATION (launch boost) slider
        card.add(boxSectionLbl("Launch Boost",bc));
        JSlider accSlider = makeSlider(0,300,0,bc);
        JLabel accVal = valLbl("0 px/s²");
        accSlider.addChangeListener(e -> {
            // stored as an extra velocity bonus at launch
            box.initVel = velSlider.getValue() + accSlider.getValue()*0.35;
            accVal.setText(String.format("+%.0f px/s",accSlider.getValue()*0.35));
        });
        card.add(accSlider); card.add(accVal);

        p_cards.add(card);
        return card;
    }
    // temp list so we can reference during layout
    List<JPanel> p_cards = new ArrayList<>();

    // direction button helper removed — handled inline

    // ── ui helpers ────────────────────────────────────────────────────────────
    Component vgap(int h){ return javax.swing.Box.createVerticalStrut(h); }

    JPanel card(String title){
        JPanel c=new JPanel(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                RoundRectangle2D box=new RoundRectangle2D.Double(.5,.5,getWidth()-1,getHeight()-1,16,16);
                g2.setPaint(new GradientPaint(0,0,CARD_T,0,getHeight(),CARD_B)); g2.fill(box);
                g2.setColor(CARD_EDGE); g2.draw(box);
                g2.dispose();
            }
        };
        c.setOpaque(false); c.setLayout(new BoxLayout(c,BoxLayout.Y_AXIS));
        c.setAlignmentX(CENTER_ALIGNMENT); c.setMaximumSize(new Dimension(Integer.MAX_VALUE,2000));
        c.setBorder(BorderFactory.createEmptyBorder(8,10,10,10));
        JLabel lbl=new JLabel(title,SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI",Font.BOLD,13)); lbl.setForeground(new Color(145,185,255));
        lbl.setAlignmentX(CENTER_ALIGNMENT); lbl.setBorder(BorderFactory.createEmptyBorder(0,0,5,0));
        c.add(lbl); return c;
    }

    JSlider makeSlider(int min, int max, int val, Color tint){
        JSlider s=new JSlider(min,max,val);
        s.setBackground(new Color(0,0,0,0)); s.setOpaque(false);
        if(tint!=null) s.setForeground(tint);
        else s.setForeground(new Color(90,140,230));
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE,28)); return s;
    }

    JLabel sectionLbl(String t){
        JLabel l=new JLabel(t); l.setFont(new Font("Segoe UI",Font.BOLD,11));
        l.setForeground(new Color(120,150,200)); l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(2,0,1,0)); return l;
    }

    JLabel boxSectionLbl(String t, Color bc){
        JLabel l=new JLabel(t); l.setFont(new Font("Segoe UI",Font.BOLD,11));
        l.setForeground(new Color(bc.getRed(),bc.getGreen(),bc.getBlue(),190));
        l.setAlignmentX(LEFT_ALIGNMENT); l.setBorder(BorderFactory.createEmptyBorder(2,0,1,0)); return l;
    }

    JLabel miniLbl(String t){
        JLabel l=new JLabel(t); l.setFont(new Font("Segoe UI",Font.PLAIN,9));
        l.setForeground(new Color(90,110,160)); return l;
    }

    JLabel valLbl(String t){
        JLabel l=new JLabel(t); l.setFont(new Font("Segoe UI",Font.BOLD,11));
        l.setForeground(new Color(210,195,130)); l.setAlignmentX(LEFT_ALIGNMENT); return l;
    }

    JToggleButton styleToggle(String text, boolean sel, Color bg){
        JToggleButton b=new JToggleButton(text,sel){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                Color base=isSelected()?new Color(bg.getRed()+30,bg.getGreen()+30,bg.getBlue()+40,220):new Color(bg.getRed(),bg.getGreen(),bg.getBlue(),160);
                g2.setPaint(new GradientPaint(0,0,base.brighter(),0,getHeight(),base));
                g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);
                if(isSelected()){g2.setColor(new Color(90,140,230,100));g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);}
                g2.dispose();super.paintComponent(g);
            }
        };
        b.setFont(new Font("Segoe UI",Font.BOLD,11));
        b.setForeground(sel?TEXT_PRI:new Color(160,175,210));
        b.setContentAreaFilled(false);b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
        b.setFocusPainted(false);
        b.addItemListener(e->b.setForeground(b.isSelected()?TEXT_PRI:new Color(160,175,210)));
        return b;
    }

    JButton bigBtn(String text, Color bg, Color fg){
        JButton b=new JButton(text){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                Color base=getModel().isPressed()?bg.darker():getModel().isRollover()?bg.brighter():bg;
                g2.setPaint(new GradientPaint(0,0,base.brighter(),0,getHeight(),base));
                g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                g2.setColor(new Color(fg.getRed(),fg.getGreen(),fg.getBlue(),80));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                g2.dispose();super.paintComponent(g);
            }
        };
        b.setForeground(fg); b.setFont(new Font("Segoe UI",Font.BOLD,13));
        b.setContentAreaFilled(false);b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(9,14,9,14));
        b.setFocusPainted(false); b.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));
        b.setAlignmentX(CENTER_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return b;
    }
}
