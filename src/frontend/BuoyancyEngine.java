// Swing UI
import javax.swing.*;
// AWT drawing, events, and geometry
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
// Collections and utilities
import java.util.*;
import java.util.List;

// Buoyancy simulation window: UI + water tank physics.
// OOP: Inherits JFrame, composes SimPanel to keep UI and physics separate.
public class BuoyancyEngine extends JFrame {

    // Screen sizing and physics scaling constants.
    static final int W = 1200, H = 800;
    static final int CTRL_W = 320;
    static final double EARTH_GRAVITY_MPS2 = 9.81;
    static final double PIXELS_PER_METER = 100.0;
    static final double AIR_DRAG_COEFF = 0.06;
    static final double G_ACCEL = EARTH_GRAVITY_MPS2 * PIXELS_PER_METER;

    static final Color BG_DEEP    = new Color(4,  6, 18);
    static final Color BG_MID     = new Color(8, 12, 28);
    static final Color PANEL_BG   = new Color(6, 10, 24);
    static final Color CARD_T     = new Color(14, 24, 52, 230);
    static final Color CARD_B     = new Color( 8, 16, 38, 230);
    static final Color CARD_EDGE  = new Color(50, 80, 165, 80);
    static final Color TEXT_PRI   = new Color(220, 232, 252);
    static final Color WATER_TOP  = new Color( 20,  90, 180, 210);
    static final Color WATER_BOT  = new Color(  8,  45, 110, 240);
    static final Color WATER_SURF = new Color( 80, 180, 255, 120);
    static final Color[] OBJ_COLS = {
        new Color(220,  70,  55),
        new Color( 55, 185,  80),
        new Color(240, 175,  40),
        new Color(160,  80, 220),
    };

    SimPanel sim;
    Runnable onBack;
    JTextArea chatDisplay;
    JTextField chatInput;
    boolean chatCollapsed = true;
    JPanel chatPanelRef;

    public static void main(String[] args) { SwingUtilities.invokeLater(BuoyancyEngine::new); }
    public BuoyancyEngine() { this(null); }
    public BuoyancyEngine(Runnable backCb) {
        this.onBack = backCb;
        setTitle("Buoyancy Physics Engine");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_DEEP);
        sim = new SimPanel();
        add(sim, BorderLayout.CENTER);
        add(buildControlPanel(), BorderLayout.EAST);
        add(buildChatPanel(), BorderLayout.SOUTH);
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle scr = ge.getMaximumWindowBounds();
        setSize(Math.min(W, scr.width), Math.min(H, scr.height));
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  FLOATING OBJECT
    // ════════════════════════════════════════════════════════════════════════
    // Physics body that moves through the fluid (mass, density, size).
    // OOP: Encapsulated state for each object; methods update its size/shape.
    static class FloatObj {
        int id;
        double x, y;          // centre
        double vx, vy;
        double mass;          // kg
        double density;       // kg/m³  (water=1000)
        double width, height; // pixels — derived from mass & density
        Color col;
        boolean pinned = false;
        boolean active = true;
        double driftBias = 0;
        // visuals
        List<Point2D.Double> trail = new ArrayList<>();
        double bobPhase = Math.random() * Math.PI * 2;
        double squishX = 1, squishY = 1;
        double splashTimer = 0;
        boolean wasSubmerged = false;
        // shape: 0=box 1=circle 2=triangle
        int shape = 0;
        String label = "";

        FloatObj(int id, Color col) {
            this.id = id; this.col = col;
            mass = 2.0; density = 600.0;
            updateSize();
            label = new String[]{"A","B","C","D"}[id];
        }

        void updateSize() {
            // volume = mass/density, display as square root scaled to pixels
            double vol = mass / density;           // m³ (scaled)
            double side = 28 + 70 * Math.sqrt(vol / 0.005);
            side = Math.max(20, Math.min(120, side));
            width = side; height = side;
        }

        double volume() { return width * height; }   // in pixel² — proportional

        // submerged fraction [0,1]
        double submergedFraction(double waterSurfaceY, double tankH) {
            double top    = y - height / 2.0;
            double bottom = y + height / 2.0;
            double waterY = waterSurfaceY;
            if (bottom <= waterY) return 0.0;            // fully above
            if (top >= waterY + tankH) return 1.0;       // fully below (shouldn't happen)
            double subH = Math.min(bottom, waterY + tankH) - Math.max(top, waterY);
            return Math.max(0, Math.min(1, subH / height));
        }

        Rectangle2D.Double rect() {
            return new Rectangle2D.Double(x-width/2*squishX, y-height/2*squishY, width*squishX, height*squishY);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  BUBBLE / SPLASH PARTICLE
    // ════════════════════════════════════════════════════════════════════════
    // Visual-only particles for surface splashes and bubbles.
    // Extra feature: visual feedback for turbulence and impacts.
    static class Bubble {
        double x, y, vx, vy, r, life, maxLife;
        boolean isSplash;
        Color c;
        Bubble(double x, double y, boolean splash) {
            this.x=x; this.y=y; isSplash=splash;
            double ang = Math.random()*Math.PI*2;
            double spd = splash ? 60+Math.random()*120 : 10+Math.random()*30;
            vx=Math.cos(ang)*spd * (splash?1:0.3);
            vy=splash ? -(30+Math.random()*80) : -(15+Math.random()*25);
            r=splash ? 2+Math.random()*4 : 1.5+Math.random()*3;
            maxLife=life=splash ? 0.5+Math.random()*0.4 : 1.0+Math.random()*1.5;
            c=splash?new Color(150,210,255):new Color(180,230,255,160);
        }
        boolean tick(double dt, double grav) {
            x+=vx*dt; y+=vy*dt;
            if (!isSplash) vy -= 80*dt;  // bubbles float up
            else { vy += grav*0.4*dt; vx*=0.93; }
            life-=dt; return life<=0;
        }
        void draw(Graphics2D g) {
            float a=(float)Math.max(0,life/maxLife);
            int alpha=Math.max(0,Math.min(255,(int)(a*(isSplash?180:130))));
            g.setColor(new Color(c.getRed(),c.getGreen(),c.getBlue(),alpha));
            int ri=Math.max(1,(int)r);
            if(isSplash) g.fillOval((int)(x-ri),(int)(y-ri),ri*2,ri*2);
            else {
                g.drawOval((int)(x-ri),(int)(y-ri),ri*2,ri*2);
                g.setColor(new Color(220,240,255,alpha/3));
                g.fillOval((int)(x-ri),(int)(y-ri),ri*2,ri*2);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SIMULATION PANEL
    // ════════════════════════════════════════════════════════════════════════
    // Owns the physics loop, rendering, and user interaction for buoyancy.
    // Logic: applies buoyancy, gravity, drag, and surface tension each tick.
    class SimPanel extends JPanel {
        FloatObj[] objects;
        List<Bubble> bubbles = new ArrayList<>();

        double fluidDensity = 1000.0;  // water = 1000 kg/m³
        double viscosity    = 0.02;    // drag coefficient
        double surfaceTension = 0.3;   // subtle surface cling
        double gravityScale = 1.0;
        double currentStrength = 0.0;
        boolean turbulence  = false;
        double turbAmp      = 0.0;
        boolean waveMode    = false;
        double waveTime     = 0;
        double waveAmp      = 18.0;
        double waveFreq     = 1.2;
        boolean paused      = false;
        double timeScale    = 1.0;

        // overlays / display toggles
        boolean showTrails      = true;
        boolean showForceArrows = true;
        boolean showLabels      = true;
        boolean showBubbles     = true;
        boolean showFlowField   = true;

        // tank
        int tankMargin = 60;
        double waterLevel = 0.48;  // fraction of tank height filled with water
        double waterSurfY;         // computed each frame
        double waterShake = 0;     // horizontal wave offset
        Color waterTopCol = WATER_TOP;
        Color waterBotCol = WATER_BOT;
        Color waterSurfCol = WATER_SURF;
        String fluidName = "Water";

        // stars
        double[] stX, stY, stR, stB;

        javax.swing.Timer loop;
        long lastMs = System.currentTimeMillis();

        // hover drag
        int dragObjIdx = -1;
        Point lastDrag = null;
        long lastDragMs = 0;

        SimPanel() {
            setBackground(BG_DEEP); setOpaque(true);
            // Background starfield points.
            Random rng = new Random(13);
            int ns=200; stX=new double[ns];stY=new double[ns];stR=new double[ns];stB=new double[ns];
            for(int i=0;i<ns;i++){stX[i]=rng.nextDouble();stY[i]=rng.nextDouble();stR[i]=rng.nextDouble()*1.3+0.3;stB[i]=rng.nextDouble()*0.5+0.2;}

            // Default objects with different densities for comparison.
            objects = new FloatObj[]{
                new FloatObj(0, OBJ_COLS[0]),
                new FloatObj(1, OBJ_COLS[1]),
                new FloatObj(2, OBJ_COLS[2]),
                new FloatObj(3, OBJ_COLS[3]),
            };
            objects[0].density = 400;  // floats well
            objects[1].density = 850;  // barely floats
            objects[2].density = 1200; // sinks
            objects[3].density = 700;  // middle
            objects[2].active  = false;
            objects[3].active  = false;
            for(FloatObj o:objects) o.updateSize();
            setFluidType(0);
            resetPositions();
            startSinkAnimation();
            setToolTipText("Drag active objects to toss them through the fluid");

            addMouseListener(new MouseAdapter(){
                public void mousePressed(MouseEvent e){
                    dragObjIdx=-1;
                    for(int i=0;i<objects.length;i++){
                        FloatObj o=objects[i]; if(!o.active)continue;
                        if(o.rect().contains(e.getX(),e.getY())){ dragObjIdx=i; lastDrag=e.getPoint(); lastDragMs=System.currentTimeMillis(); o.pinned=true; break;}
                    }
                }
                public void mouseReleased(MouseEvent e){
                    // Release: stop pinning and dampen throw speed.
                    if(dragObjIdx>=0){
                        FloatObj o=objects[dragObjIdx];
                        o.pinned=false;
                        o.vx=o.vx*0.65+o.driftBias*0.35;
                        o.vy*=0.65;
                    }
                    dragObjIdx=-1;
                    lastDrag=null;
                    lastDragMs=0;
                }
            });
            addMouseMotionListener(new MouseMotionAdapter(){
                public void mouseDragged(MouseEvent e){
                    if(dragObjIdx>=0&&lastDrag!=null){
                        FloatObj o=objects[dragObjIdx];
                        long now=System.currentTimeMillis();
                        double dtS=Math.max(0.001,(now-lastDragMs)/1000.0);
                        int dx = e.getX() - lastDrag.x;
                        int dy = e.getY() - lastDrag.y;
                        o.x += dx; o.y += dy;
                        // Convert mouse delta to a gentle velocity.
                        o.vx = dx/dtS*0.25;
                        o.vy = dy/dtS*0.25;
                        waterShake += dx * 0.04;
                        lastDrag = e.getPoint();
                        lastDragMs=now;
                    }
                }
            });

            loop = new javax.swing.Timer(15, e -> {
                long now=System.currentTimeMillis();
                double dt=Math.min((now-lastMs)/1000.0, 0.04);
                lastMs=now;
                tick(dt);
                repaint();
            });
            loop.start();
        }

        void resetPositions() {
            // Line up objects near the surface for a predictable start.
            int pw=getWidth()>10?getWidth():W-CTRL_W;
            int tankLeft=tankMargin, tankW=pw-tankMargin*2;
            int tankTop=tankMargin;
            int[] xs={tankLeft+tankW/5, tankLeft+2*tankW/5, tankLeft+3*tankW/5, tankLeft+4*tankW/5};
            for(int i=0;i<objects.length;i++){
                FloatObj o=objects[i];
                o.x=xs[i];
                o.y=tankTop+o.height*0.55+8+i*2;
                o.vx=o.driftBias*0.05;
                o.vy=0;
                o.trail.clear();
                o.squishX=o.squishY=1;
                o.wasSubmerged=false;
            }
        }

        void setFluidType(int type){
            // Swap physical properties + colors for each fluid preset.
            switch(type){
                case 0:
                    fluidName="Water";
                    fluidDensity=1000;viscosity=0.02; surfaceTension=0.30;
                    waterTopCol=new Color(20,90,180,210);
                    waterBotCol=new Color(8,45,110,240);
                    waterSurfCol=new Color(80,180,255,120);
                    break;
                case 1:
                    fluidName="Oil";
                    fluidDensity=800; viscosity=0.03; surfaceTension=0.22;
                    waterTopCol=new Color(198,145,70,205);
                    waterBotCol=new Color(118,74,28,240);
                    waterSurfCol=new Color(246,198,110,130);
                    break;
                case 2:
                    fluidName="Mercury";
                    fluidDensity=1360;viscosity=0.06; surfaceTension=0.12;
                    waterTopCol=new Color(175,188,208,220);
                    waterBotCol=new Color(92,104,126,245);
                    waterSurfCol=new Color(224,232,246,140);
                    break;
                case 3:
                    fluidName="Salt Water";
                    fluidDensity=1200;viscosity=0.04; surfaceTension=0.36;
                    waterTopCol=new Color(35,124,186,214);
                    waterBotCol=new Color(8,69,136,244);
                    waterSurfCol=new Color(110,214,255,132);
                    break;
                case 4:
                    fluidName="Petrol";
                    fluidDensity=680; viscosity=0.008;surfaceTension=0.18;
                    waterTopCol=new Color(42,125,88,206);
                    waterBotCol=new Color(16,80,52,243);
                    waterSurfCol=new Color(120,240,178,122);
                    break;
            }
        }

        void startSinkAnimation(){
            // Drop active objects evenly across the tank.
            int pw=getWidth()>10?getWidth():W-CTRL_W;
            int tankLeft=tankMargin, tankRight=pw-tankMargin;
            int tankTop=tankMargin;

            int activeCount=0;
            for(FloatObj o:objects) if(o.active) activeCount++;
            if(activeCount==0) return;

            int laneIdx=0;
            for(FloatObj o:objects){
                if(!o.active) continue;
                double lane=(laneIdx+1.0)/(activeCount+1.0);
                o.x=tankLeft+lane*(tankRight-tankLeft);
                o.y=tankTop+o.height*0.55+8+laneIdx*2;

                o.vx=o.driftBias*0.05;
                o.vy=0;
                o.trail.clear();
                o.wasSubmerged=false;
                o.squishX=o.squishY=1;
                laneIdx++;
            }
            waterShake+=(Math.random()-0.5)*22;
        }

        void randomizeScene(){
            // Randomize masses, densities, and fluid conditions for exploration.
            Random rng = new Random();
            for(FloatObj o:objects){
                o.mass = 1 + rng.nextInt(40);
                o.density = 220 + rng.nextInt(2500);
                o.shape = rng.nextInt(3);
                o.driftBias = -120 + rng.nextDouble()*240;
                o.updateSize();
                o.trail.clear();
            }
            waterLevel = 0.28 + rng.nextDouble()*0.52;
            waveAmp = 8 + rng.nextDouble()*34;
            waveFreq = 0.5 + rng.nextDouble()*2.3;
            turbAmp = rng.nextDouble()*0.8;
            waterShake += (rng.nextDouble()-0.5)*50;
            resetPositions();
            for(FloatObj o:objects){
                if(!o.active) continue;
                o.vx += (rng.nextDouble()-0.5)*120;
                o.vy = -40 - rng.nextDouble()*130;
            }
            bubbles.clear();
        }

        void stirFluid(){
            // Kick objects and spawn splashes to simulate a stir.
            int pw=getWidth()>10?getWidth():W-CTRL_W;
            int ph=getHeight()>10?getHeight():H;
            int tankLeft=tankMargin, tankRight=pw-tankMargin;
            int tankBottom=ph-tankMargin;
            double tankH=tankBottom-tankMargin;
            waveMode = true;
            waterShake += (Math.random()-0.5)*60;
            for(FloatObj o:objects){
                if(!o.active||o.pinned) continue;
                double frac=o.submergedFraction(waterSurfY,tankH);
                if(frac<=0) continue;
                o.vx += (Math.random()-0.5)*(120+140*frac);
                o.vy += (Math.random()-0.5)*(70+80*frac);
            }
            for(int i=0;i<18;i++){
                int sx=(int)(tankLeft+Math.random()*(tankRight-tankLeft));
                int sy=(int)waterSurfY;
                bubbles.add(new Bubble(sx,sy,true));
            }
        }

        void dropObjects(){
            // Reposition objects for a fresh drop test.
            int pw=getWidth()>10?getWidth():W-CTRL_W;
            int tankLeft=tankMargin, tankRight=pw-tankMargin;
            int tankTop=tankMargin;
            for(FloatObj o:objects){
                if(!o.active) continue;
                o.x=tankLeft+o.width/2+10+Math.random()*(tankRight-tankLeft-o.width-20);
                o.y=tankTop+o.height*0.55+8+Math.random()*50;
                o.vx=o.driftBias+(Math.random()-0.5)*90;
                o.vy=0;
                o.trail.clear();
                o.wasSubmerged=false;
            }
        }

        // ── physics ───────────────────────────────────────────────────────
        // Logic: integrates forces (buoyancy vs gravity) and damping per frame.
        void tick(double dt) {
            // One physics step: update water surface, then move objects.
            int pw=getWidth()>10?getWidth():W-CTRL_W;
            int ph=getHeight()>10?getHeight():H;
            int tankLeft=tankMargin, tankRight=pw-tankMargin;
            int tankTop=tankMargin, tankBottom=ph-tankMargin;
            double tankH=tankBottom-tankTop;

            double g = G_ACCEL * gravityScale;
            double simDt = paused ? 0 : dt * timeScale;

            waveTime+=dt*(paused?0.25:Math.max(0.35,timeScale));
            waterShake*=paused?0.96:0.92;
            // Wave offset drives the animated water surface.
            double waveOffset = waveMode ? waveAmp*Math.sin(waveTime*waveFreq*Math.PI*2) : 0;
            waterSurfY = tankBottom - tankH*waterLevel + waveOffset;

            double bubbleDt = paused ? dt*0.12 : simDt;
            bubbles.removeIf(b->b.tick(bubbleDt, g));

            if(simDt<=0) return;

            for(FloatObj o:objects){
                if(!o.active||o.pinned)continue;

                double frac = o.submergedFraction(waterSurfY, tankH);
                double displacedVol = o.mass / Math.max(1.0, o.density);
                double centreRelSurf = o.y - waterSurfY;

                // In air: free fall under earth gravity before buoyancy engages.
                if(frac <= 0.001){
                    o.vy += g * simDt;
                    o.vx *= Math.max(0,1 - AIR_DRAG_COEFF * simDt);
                    o.vy *= Math.max(0,1 - AIR_DRAG_COEFF * simDt * 0.6);
                } else {
                    // buoyancy: F = rho_fluid * g * V_submerged
                    double Fb = fluidDensity * g * displacedVol * frac;

                    // gravity: F = m * g
                    double Fg = o.mass * g;

                    // net vertical force in screen space (positive y is downward)
                    double Fnet = Fg - Fb;

                    // Added-mass term: moving fluid adds inertia and reduces overshoot.
                    double addedMass = fluidDensity * displacedVol * (0.35 + 0.45 * frac);
                    double effectiveMass = o.mass + addedMass;

                    // viscous drag (proportional to velocity, stronger when submerged)
                    double dragMassFactor = 1.0 / (0.65 + Math.sqrt(o.mass));
                    double dragH = viscosity * (1 + frac * 3) * dragMassFactor;
                    double dragV = viscosity * (1 + frac * 3) * dragMassFactor;

                    o.vx *= Math.max(0,1 - dragH * simDt * 8);
                    o.vy += (Fnet / effectiveMass) * simDt;
                    o.vy *= Math.max(0,1 - dragV * simDt * 6);

                    // fluid current tries to pull objects toward a flow velocity
                    double flowTarget = currentStrength * (0.35 + frac * 0.9);
                    o.vx += (flowTarget - o.vx) * frac * simDt * 1.2;

                    // surface tension: small snap near waterline
                    if(Math.abs(centreRelSurf) < o.height*0.6 && frac>0.05 && frac<0.95){
                        o.vy -= centreRelSurf * surfaceTension * simDt * 0.28;
                        o.vy *= 0.988;
                    }

                    // Strong upward braking near the interface prevents "rocket" jumps.
                    if(o.vy<0 && centreRelSurf<o.height*0.35){
                        double nearSurface = 1.0 - Math.min(1.0, Math.max(0.0, (centreRelSurf + o.height*0.9) / (o.height*1.2)));
                        if(nearSurface>0){
                            double maxUpSpeed = -120 - 100*frac;
                            o.vy = Math.max(o.vy, maxUpSpeed);
                            o.vy *= Math.max(0.42, 1.0 - nearSurface*0.5);
                        }
                    }

                    if(frac<0.08){
                        o.vx *= Math.max(0,1 - simDt*0.7);
                    }

                    // turbulence adds random jitter to the flow
                    if(turbulence && turbAmp>0){
                        o.vx += (Math.random()-0.5)*turbAmp*simDt*80*frac;
                        o.vy += (Math.random()-0.5)*turbAmp*simDt*40*frac;
                    }
                }

                // safety clamp keeps energy bounded and prevents numerical explosions.
                o.vx=Math.max(-520,Math.min(520,o.vx));
                o.vy=Math.max(-620,Math.min(620,o.vy));

                o.x += o.vx*simDt;
                o.y += o.vy*simDt;

                // water surface splash/entry
                boolean nowSubm = frac > 0.15;
                if(!o.wasSubmerged && nowSubm && Math.abs(o.vy)>40){
                    spawnSplash((int)o.x,(int)(waterSurfY),o.col,12);
                    o.squishX=1.4;o.squishY=0.7;o.splashTimer=0.3;
                }
                if(o.wasSubmerged && !nowSubm){
                    spawnSplash((int)o.x,(int)(waterSurfY),o.col,8);
                }
                o.wasSubmerged=nowSubm;
                if(o.splashTimer>0){o.splashTimer-=simDt;if(o.splashTimer<=0){o.squishX=1;o.squishY=1;}}

                // bubble generation when submerged fast
                if(showBubbles && frac>0.5 && Math.abs(o.vy)>20 && Math.random()<0.25)
                    bubbles.add(new Bubble(o.x+(Math.random()-0.5)*o.width*0.7,o.y-o.height*0.3,false));

                // recovery squish
                o.squishX+=(1-o.squishX)*0.15;
                o.squishY+=(1-o.squishY)*0.15;

                // tank walls
                double halfW=o.width/2, halfH=o.height/2;
                if(o.x-halfW<tankLeft){o.x=tankLeft+halfW;o.vx=Math.abs(o.vx)*0.4;}
                if(o.x+halfW>tankRight){o.x=tankRight-halfW;o.vx=-Math.abs(o.vx)*0.4;}
                if(o.y+halfH>tankBottom){o.y=tankBottom-halfH;o.vy=-Math.abs(o.vy)*0.3;o.vx*=0.7;spawnSplash((int)o.x,(int)o.y,o.col,4);}
                if(o.y-halfH<tankTop){o.y=tankTop+halfH;o.vy=Math.max(16,Math.abs(o.vy)*0.45);}

                // trail
                if(showTrails){
                    o.trail.add(new Point2D.Double(o.x,o.y));
                    if(o.trail.size()>60)o.trail.remove(0);
                } else if(!o.trail.isEmpty()){
                    o.trail.clear();
                }

                // ambient bubbles underwater
                if(showBubbles && frac>0.8&&Math.random()<0.04)
                    bubbles.add(new Bubble(o.x+(Math.random()-0.5)*o.width*0.6,o.y+o.height*0.4,false));
            }

            // object-object collisions in water
            FloatObj[] active = Arrays.stream(objects).filter(o->o.active&&!o.pinned).toArray(FloatObj[]::new);
            for(int i=0;i<active.length;i++)for(int j=i+1;j<active.length;j++) resolveBoxCollision(active[i],active[j]);

            // passive ambient bubbles rising
            if(showBubbles && Math.random()<0.06&&waterLevel>0.1)
                bubbles.add(new Bubble(tankLeft+(Math.random()*(tankRight-tankLeft)),(int)(waterSurfY+tankH*0.6),false));
        }

        void spawnSplash(int x,int y,Color c,int n){for(int i=0;i<n;i++)bubbles.add(new Bubble(x,y,true));}

        void resolveBoxCollision(FloatObj a, FloatObj b){
            // Simple AABB collision resolution + impulse response.
            double dx=b.x-a.x,dy=b.y-a.y;
            double ox=(a.width+b.width)/2-Math.abs(dx), oy=(a.height+b.height)/2-Math.abs(dy);
            if(ox<=0||oy<=0)return;
            double nx,ny,ov;
            if(ox<oy){nx=dx<0?-1:1;ny=0;ov=ox;}else{nx=0;ny=dy<0?-1:1;ov=oy;}
            double tm=a.mass+b.mass;
            a.x-=nx*ov*(b.mass/tm); a.y-=ny*ov*(b.mass/tm);
            b.x+=nx*ov*(a.mass/tm); b.y+=ny*ov*(a.mass/tm);
            double rvn=(b.vx-a.vx)*nx+(b.vy-a.vy)*ny;
            if(rvn>0)return;
            double e=0.35;
            double j=-(1+e)*rvn/(1.0/a.mass+1.0/b.mass);
            a.vx-=j/a.mass*nx;a.vy-=j/a.mass*ny;
            b.vx+=j/b.mass*nx;b.vy+=j/b.mass*ny;
            a.squishX=nx==0?1.2:0.8;a.squishY=ny==0?1.2:0.8;
            b.squishX=nx==0?1.2:0.8;b.squishY=ny==0?1.2:0.8;
            spawnSplash((int)((a.x+b.x)/2),(int)((a.y+b.y)/2),mixCol(a.col,b.col),6);
        }
        static Color mixCol(Color a,Color b){return new Color((a.getRed()+b.getRed())/2,(a.getGreen()+b.getGreen())/2,(a.getBlue()+b.getBlue())/2);}

        // ── paint ─────────────────────────────────────────────────────────
        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2=(Graphics2D)g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int pw=getWidth(),ph=getHeight();
            drawBg(g2,pw,ph);
            drawStars(g2,pw,ph);
            drawTank(g2,pw,ph);
            drawWater(g2,pw,ph);
            if(showFlowField) drawFlowField(g2,pw,ph);
            if(showTrails) drawTrails(g2);
            if(showBubbles) for(Bubble b:bubbles)b.draw(g2);
            drawObjects(g2,pw,ph);
            drawHUD(g2,pw);
        }

        void drawBg(Graphics2D g2,int pw,int ph){
            g2.setPaint(new GradientPaint(pw/2f,0,BG_MID,pw/2f,ph,BG_DEEP));
            g2.fillRect(0,0,pw,ph);
            g2.setStroke(new BasicStroke(.4f));
            g2.setColor(new Color(22,38,80,35));
            for(int x=0;x<pw;x+=50)g2.drawLine(x,0,x,ph);
            for(int y=0;y<ph;y+=50)g2.drawLine(0,y,pw,y);
            g2.setStroke(new BasicStroke(1));
        }
        void drawStars(Graphics2D g2,int pw,int ph){
            long t=System.currentTimeMillis();
            for(int i=0;i<stX.length;i++){
                float tw=(float)(stB[i]*(0.45+0.55*Math.sin(t*.0003*stB[i]*3+i)));
                g2.setColor(new Color(1f,1f,1f,Math.max(.03f,Math.min(.6f,tw))));
                int r=Math.max(1,(int)(stR[i]*1.2));
                g2.fillOval((int)(stX[i]*pw),(int)(stY[i]*ph),r,r);
            }
        }
        void drawTank(Graphics2D g2,int pw,int ph){
            int tl=tankMargin,tr=pw-tankMargin,tt=tankMargin,tb=ph-tankMargin;
            // glass panel background
            g2.setColor(new Color(10,20,48,60));
            g2.fillRoundRect(tl,tt,tr-tl,tb-tt,18,18);
            // glass shimmer effect
            GradientPaint shimmer=new GradientPaint(tl,tt,new Color(120,180,255,18),tr,tb,new Color(60,120,220,6));
            g2.setPaint(shimmer); g2.fillRoundRect(tl,tt,tr-tl,tb-tt,18,18);
            // borders
            g2.setStroke(new BasicStroke(2.2f));
            g2.setColor(new Color(70,120,220,180));
            g2.drawRoundRect(tl,tt,tr-tl,tb-tt,18,18);
            // corner glints
            g2.setColor(new Color(160,210,255,90));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(tl+4,tt+4,tl+16,tt+4); g2.drawLine(tl+4,tt+4,tl+4,tt+16);
            g2.drawLine(tr-16,tt+4,tr-4,tt+4); g2.drawLine(tr-4,tt+4,tr-4,tt+16);
            // water level ruler on right side
            g2.setFont(new Font("Segoe UI",Font.PLAIN,10));
            g2.setColor(new Color(80,130,220,140));
            for(int pct=0;pct<=100;pct+=10){
                int ry=(int)(tb-(tb-tt)*pct/100.0);
                g2.drawLine(tr+2,ry,tr+8,ry);
                if(pct%20==0) g2.drawString(pct+"%",tr+10,ry+4);
            }
            g2.setStroke(new BasicStroke(1));
        }

        void drawWater(Graphics2D g2,int pw,int ph){
            // Draw the animated water surface and fill.
            int tl=tankMargin,tr=pw-tankMargin,tb=ph-tankMargin;
            int surfY=(int)waterSurfY;
            if(surfY>=tb)return;

            // animated wave surface path
            Path2D.Double wave=new Path2D.Double();
            wave.moveTo(tl,surfY);
            int steps=80;
            double wt=waveTime*waveFreq*Math.PI*2;
            for(int i=0;i<=steps;i++){
                double fx=(double)i/steps;
                double wx=tl+fx*(tr-tl);
                double wy=surfY+Math.sin(wt+fx*Math.PI*5)*3.5
                           +Math.sin(wt*1.3+fx*Math.PI*3)*1.8
                           +(waveMode?Math.sin(wt*0.7+fx*Math.PI*2)*waveAmp*0.5:0);
                if(i==0)wave.moveTo(wx,wy); else wave.lineTo(wx,wy);
            }
            wave.lineTo(tr,tb); wave.lineTo(tl,tb); wave.closePath();

            // clip to tank interior
            Shape oldClip=g2.getClip();
            g2.setClip(new RoundRectangle2D.Double(tl+2,tankMargin+2,tr-tl-4,tb-tankMargin-4,16,16));

            // water fill gradient
            GradientPaint waterFill=new GradientPaint(tl,surfY,waterTopCol,tl,tb,waterBotCol);
            g2.setPaint(waterFill); g2.fill(wave);

            // caustic light beams (animated)
            double ct=waveTime*0.8;
            for(int i=0;i<8;i++){
                double cx2=tl+(tr-tl)*((i+0.5)/8.0+Math.sin(ct+i*1.1)*0.05);
                double cw=12+8*Math.sin(ct*1.3+i);
                float ca=(float)(0.03+0.02*Math.sin(ct+i));
                GradientPaint caustic=new GradientPaint((float)cx2,(float)surfY,
                    new Color(Math.min(255,waterSurfCol.getRed()+70),Math.min(255,waterSurfCol.getGreen()+55),Math.min(255,waterSurfCol.getBlue()+45),(int)(ca*255)),
                    (float)cx2,(float)tb,new Color(waterBotCol.getRed(),waterBotCol.getGreen(),waterBotCol.getBlue(),0));
                g2.setPaint(caustic);
                g2.fillRect((int)(cx2-cw/2),(int)surfY,(int)cw,(int)(tb-surfY));
            }

            // subsurface depth fog
            GradientPaint depthFog=new GradientPaint(tl,surfY,
                new Color(waterTopCol.getRed(),waterTopCol.getGreen(),waterTopCol.getBlue(),0),
                tl,tb,new Color(waterBotCol.getRed()/2,waterBotCol.getGreen()/2,waterBotCol.getBlue()/2,85));
            g2.setPaint(depthFog); g2.fill(wave);

            g2.setClip(oldClip);

            // surface line glow
            g2.setStroke(new BasicStroke(1.8f));
            g2.setColor(waterSurfCol);
            g2.draw(wave);

            // foam highlights on crests
            g2.setStroke(new BasicStroke(1f));
            for(int i=0;i<=steps;i+=4){
                double fx=(double)i/steps;
                double wx=tl+fx*(tr-tl);
                double wy=surfY+Math.sin(wt+fx*Math.PI*5)*3.5+Math.sin(wt*1.3+fx*Math.PI*3)*1.8;
                float fa=(float)(0.3+0.4*Math.sin(wt+fx*Math.PI*5));
                if(fa>0.45){g2.setColor(new Color(220,240,255,(int)(fa*120)));g2.fillOval((int)(wx-3),(int)(wy-2),6,3);}
            }
            g2.setStroke(new BasicStroke(1));
        }

        void drawFlowField(Graphics2D g2,int pw,int ph){
            // Debug-style arrows showing water flow direction.
            int tl=tankMargin+16,tr=pw-tankMargin-16,tb=ph-tankMargin-12;
            int sy=(int)waterSurfY+10;
            if(sy>=tb) return;
            int cols=9,rows=5;
            for(int r=0;r<rows;r++){
                double fy=(rows==1)?0.5:(double)r/(rows-1);
                int y=(int)(sy+fy*(tb-sy));
                for(int c=0;c<cols;c++){
                    double fx=(cols==1)?0.5:(double)c/(cols-1);
                    int x=(int)(tl+fx*(tr-tl));
                    double localWave=waveMode?Math.sin(waveTime*waveFreq*2+fx*Math.PI*2+fy*2.2)*22:0;
                    double flow=currentStrength+localWave;
                    double dir=flow>=0?1:-1;
                    double len=Math.min(18,5+Math.abs(flow)*0.07);
                    int x2=(int)(x+dir*len);
                    int alpha=(int)Math.min(155,42+Math.abs(flow)*0.35);
                    g2.setColor(new Color(130,205,255,alpha));
                    g2.setStroke(new BasicStroke(1.2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                    g2.drawLine(x,y,x2,y);
                    int hx=(int)(x2-dir*5);
                    g2.drawLine(x2,y,hx,y-3);
                    g2.drawLine(x2,y,hx,y+3);
                }
            }
            g2.setStroke(new BasicStroke(1));
        }

        void drawTrails(Graphics2D g2){
            for(FloatObj o:objects){
                if(!o.active||o.trail.size()<2)continue;
                g2.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                for(int i=1;i<o.trail.size();i++){
                    float a=(float)i/o.trail.size()*0.28f;
                    g2.setColor(new Color(o.col.getRed(),o.col.getGreen(),o.col.getBlue(),(int)(a*255)));
                    Point2D.Double p0=o.trail.get(i-1),p1=o.trail.get(i);
                    g2.drawLine((int)p0.x,(int)p0.y,(int)p1.x,(int)p1.y);
                }
                g2.setStroke(new BasicStroke(1));
            }
        }

        void drawObjects(Graphics2D g2,int pw,int ph){
            // Render objects with underwater tint and force arrows.
            // clip to tank
            int tl=tankMargin,tr=pw-tankMargin,tt=tankMargin,tb=ph-tankMargin;
            Shape oldClip=g2.getClip();
            g2.setClip(new RoundRectangle2D.Double(tl+2,tt+2,tr-tl-4,tb-tt-4,16,16));

            for(FloatObj o:objects){
                if(!o.active)continue;
                double frac=o.submergedFraction(waterSurfY,tb-tt);
                double bx=o.x-o.width/2*o.squishX,by2=o.y-o.height/2*o.squishY;
                double bw=o.width*o.squishX,bh=o.height*o.squishY;

                // shadow under object on water bed
                if(frac>0.3){
                    g2.setColor(new Color(0,0,0,40));
                    g2.fillOval((int)(o.x-bw/2),(int)(tb-6),(int)bw,8);
                }

                // underwater tint overlay setup
                boolean fullyUnder=frac>=1;

                // glow
                RadialGradientPaint glow=new RadialGradientPaint((float)o.x,(float)o.y,(float)(o.width*1.2),
                    new float[]{0f,.5f,1f},
                    new Color[]{new Color(o.col.getRed(),o.col.getGreen(),o.col.getBlue(),fullyUnder?18:35),
                        new Color(o.col.getRed(),o.col.getGreen(),o.col.getBlue(),10),new Color(0,0,0,0)});
                g2.setPaint(glow);g2.fillOval((int)(o.x-o.width*1.2),(int)(o.y-o.height*1.2),(int)(o.width*2.4),(int)(o.height*2.4));

                Shape bodyShape=makeShape(o,bx,by2,bw,bh);

                // body fill
                GradientPaint fill=new GradientPaint((float)bx,(float)by2,
                    new Color(Math.min(255,o.col.getRed()+55),Math.min(255,o.col.getGreen()+40),Math.min(255,o.col.getBlue()+35)),
                    (float)bx,(float)(by2+bh),new Color(o.col.getRed()/2,o.col.getGreen()/2,o.col.getBlue()/2));
                g2.setPaint(fill);
                g2.fill(bodyShape);

                // underwater desaturation + blue tint
                if(frac>0){
                    int ua=(int)(frac*75);
                    g2.setColor(new Color(20,80,150,ua));
                    g2.fill(bodyShape);
                }

                // top highlight clipped to object shape
                Shape oldObjClip=g2.getClip();
                g2.setClip(bodyShape);
                GradientPaint rimPaint=new GradientPaint((float)bx,(float)by2,new Color(255,255,255,frac<0.5?65:36),(float)bx,(float)(by2+bh*0.4),new Color(255,255,255,0));
                g2.setPaint(rimPaint);
                g2.fillRect((int)bx,(int)by2,(int)Math.ceil(bw),(int)Math.ceil(Math.max(5,bh*0.45)));
                g2.setClip(oldObjClip);

                // border
                g2.setColor(new Color(o.col.getRed(),o.col.getGreen(),o.col.getBlue(),frac>0.5?140:190));
                g2.setStroke(new BasicStroke(1.8f));
                g2.draw(bodyShape);
                g2.setStroke(new BasicStroke(1));

                FontMetrics fm=g2.getFontMetrics();
                if(showLabels){
                    // label
                    g2.setFont(new Font("Segoe UI",Font.BOLD,12));
                    g2.setColor(new Color(255,255,255,frac>0.8?140:200));
                    fm=g2.getFontMetrics();
                    g2.drawString(o.label,(int)(o.x-fm.stringWidth(o.label)/2.0),(int)(o.y+4));
                }

                // buoyancy force arrow
                double g=G_ACCEL*gravityScale;
                double displacedVol=o.mass/Math.max(1.0,o.density);
                double fb=fluidDensity*g*displacedVol*frac;
                double fg=o.mass*g;
                double net=fb-fg;
                if(showForceArrows && Math.abs(net)>2){
                    double arrowLen=Math.min(Math.abs(net)*0.08,72);
                    boolean up=net>0;
                    Color ac=up?new Color(80,220,120):new Color(220,80,80);
                    int ax=(int)o.x, ay=(int)(o.y-(up?o.height/2+8:-o.height/2-8));
                    int ex=(int)o.x, ey=(int)(ay-(up?arrowLen:-arrowLen));
                    g2.setStroke(new BasicStroke(2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                    g2.setColor(new Color(ac.getRed(),ac.getGreen(),ac.getBlue(),160));
                    g2.drawLine(ax,ay,ex,ey);
                    double ang=up?-Math.PI/2:Math.PI/2;
                    int[]hx2={(int)ex,(int)(ex-8*Math.cos(ang-0.4)),(int)(ex-8*Math.cos(ang+0.4))};
                    int[]hy2={(int)ey,(int)(ey-8*Math.sin(ang-0.4)),(int)(ey-8*Math.sin(ang+0.4))};
                    g2.setColor(ac);g2.fillPolygon(hx2,hy2,3);
                    g2.setStroke(new BasicStroke(1));
                    if(showLabels){
                        g2.setFont(new Font("Segoe UI",Font.PLAIN,9));
                        g2.setColor(new Color(ac.getRed(),ac.getGreen(),ac.getBlue(),180));
                        g2.drawString(String.format("%+.0fN",net),(int)(ex+4),(int)((ay+ey)/2.0));
                    }
                }

                if(showLabels){
                    g2.setFont(new Font("Segoe UI",Font.PLAIN,10));
                    g2.setColor(new Color(220,235,255,frac>0.5?110:150));
                    String dl=String.format("%.0f kg/m³",o.density);
                    fm=g2.getFontMetrics();
                    g2.drawString(dl,(int)(o.x-fm.stringWidth(dl)/2),(int)(o.y+o.height/2*o.squishY+13));
                }
            }

            g2.setClip(oldClip);
        }

        Shape makeShape(FloatObj o,double bx,double by,double bw,double bh){
            switch(o.shape){
                case 1:
                    return new Ellipse2D.Double(bx,by,bw,bh);
                case 2:
                    Path2D.Double tri=new Path2D.Double();
                    tri.moveTo(bx+bw/2.0,by+1.5);
                    tri.lineTo(bx+bw-1.5,by+bh-1.5);
                    tri.lineTo(bx+1.5,by+bh-1.5);
                    tri.closePath();
                    return tri;
                default:
                    return new RoundRectangle2D.Double(bx,by,bw,bh,10,10);
            }
        }

        void drawHUD(Graphics2D g2,int pw){
            // fluid info top-left
            int hx=tankMargin+8,hy=tankMargin+8;
            g2.setColor(new Color(8,14,32,185));g2.fillRoundRect(hx,hy,226,66,10,10);
            g2.setColor(new Color(50,90,180,90));g2.drawRoundRect(hx,hy,226,66,10,10);
            g2.setFont(new Font("Segoe UI",Font.BOLD,11));g2.setColor(new Color(120,180,255,220));
            g2.drawString("FLUID: "+fluidName,hx+10,hy+16);
            g2.setFont(new Font("Segoe UI",Font.PLAIN,10));g2.setColor(new Color(160,195,240,185));
            g2.drawString(String.format("ρ=%.0f kg/m³   η=%.3f",fluidDensity,viscosity),hx+10,hy+30);
            g2.drawString(String.format("Water Level: %.0f%%",waterLevel*100),hx+10,hy+43);
            g2.drawString(String.format("Current: %+.0f px/s   g: %.2fx",currentStrength,gravityScale),hx+10,hy+56);

            int activeCount=0;
            for(FloatObj o:objects) if(o.active) activeCount++;
            String status=(paused?"PAUSED":"LIVE")+String.format("  •  %.2fx  •  Active %d",timeScale,activeCount);
            FontMetrics sfm;
            g2.setFont(new Font("Segoe UI",Font.BOLD,11));
            sfm=g2.getFontMetrics();
            int sw=sfm.stringWidth(status)+26;
            int sx=pw/2-sw/2;
            int sy=tankMargin-28;
            g2.setColor(new Color(8,14,32,190));
            g2.fillRoundRect(sx,sy,sw,20,10,10);
            g2.setColor(new Color(paused?220:100,paused?170:210,120,95));
            g2.drawRoundRect(sx,sy,sw,20,10,10);
            g2.setColor(paused?new Color(255,205,120):new Color(140,230,180));
            g2.drawString(status,sx+13,sy+14);

            // wave indicator
            if(waveMode){
                g2.setFont(new Font("Segoe UI",Font.BOLD,10));
                g2.setColor(new Color(100,200,255,200));
                g2.drawString("⟿ WAVE MODE",pw/2-35,tankMargin-5);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CONTROL PANEL
    // ════════════════════════════════════════════════════════════════════════
    JPanel buildControlPanel(){
        JPanel outer=new JPanel(new BorderLayout());
        outer.setBackground(PANEL_BG);
        outer.setPreferredSize(new Dimension(CTRL_W,H));
        outer.setBorder(BorderFactory.createMatteBorder(0,1,0,0,new Color(35,60,130)));

        JPanel p=new JPanel(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setPaint(new GradientPaint(0,0,new Color(7,11,25),0,getHeight(),new Color(5,9,20)));
                g2.fillRect(0,0,getWidth(),getHeight());
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(12,10,12,10));

        // header
        JLabel hero=lbl("BUOYANCY LAB",new Font("Segoe UI",Font.BOLD,18),new Color(160,220,255));
        hero.setAlignmentX(CENTER_ALIGNMENT);
        JLabel sub=lbl("Fluid Physics Engine",new Font("Segoe UI",Font.PLAIN,12),new Color(100,150,210));
        sub.setAlignmentX(CENTER_ALIGNMENT);
        p.add(hero);p.add(vg(2));p.add(sub);p.add(vg(10));

        // ── Object cards ──
        String[] labels={"Object A","Object B","Object C","Object D"};
        String[] shapeNames={"Rounded Box","Sphere","Pyramid"};
        List<Runnable> floatStateUpdaters=new ArrayList<>();
        for(int i=0;i<4;i++){
            FloatObj o=sim.objects[i];
            JPanel card=card2("OBJ "+labels[i].split(" ")[1],OBJ_COLS[i]);

            // active toggle
            JPanel hdr=row2(); hdr.setMaximumSize(new Dimension(Integer.MAX_VALUE,28));
            JLabel tit=lbl(labels[i].toUpperCase(),new Font("Segoe UI",Font.BOLD,12),OBJ_COLS[i]);
            JToggleButton actBtn=togBtn("ON",o.active,OBJ_COLS[i].darker().darker());
            JToggleButton pinBtn=togBtn("PIN",o.pinned,new Color(30,45,85));
            pinBtn.setFont(new Font("Segoe UI",Font.BOLD,9));
            actBtn.addActionListener(e->{o.active=actBtn.isSelected();actBtn.setText(o.active?"ON":"OFF");sim.resetPositions();});
            pinBtn.addActionListener(e->{o.pinned=pinBtn.isSelected();if(o.pinned){o.vx=o.vy=0;}});
            JPanel tRow=new JPanel();
            tRow.setOpaque(false);
            tRow.setLayout(new BoxLayout(tRow,BoxLayout.X_AXIS));
            tRow.add(actBtn);
            tRow.add(javax.swing.Box.createHorizontalStrut(4));
            tRow.add(pinBtn);
            hdr.add(tit,BorderLayout.WEST);hdr.add(tRow,BorderLayout.EAST);
            card.add(hdr);card.add(vg(5));

            // MASS
            card.add(clbl("Mass (kg)",OBJ_COLS[i]));
            JSlider ms=slider2(1,50,(int)o.mass,OBJ_COLS[i]);
            JLabel mv=valLbl(String.format("%.0f kg",o.mass));
            ms.addChangeListener(e->{o.mass=ms.getValue();mv.setText(String.format("%.0f kg",(double)ms.getValue()));o.updateSize();sim.resetPositions();});
            card.add(ms);card.add(mv);card.add(vg(4));

            // DENSITY
            card.add(clbl("Density (kg/m³)",OBJ_COLS[i]));
            JSlider ds=slider2(50,3000,(int)o.density,OBJ_COLS[i]);
            JLabel dv=valLbl(String.format("%.0f kg/m³",o.density));
            ds.addChangeListener(e->{o.density=ds.getValue();dv.setText(String.format("%.0f kg/m³",(double)ds.getValue()));o.updateSize();sim.resetPositions();});
            card.add(ds);card.add(dv);

            // float indicator
            JLabel fi=lbl(o.density<1000?"▲ FLOATS":"▼ SINKS",new Font("Segoe UI",Font.BOLD,10),o.density<1000?new Color(80,220,120):new Color(220,80,80));
            fi.setAlignmentX(LEFT_ALIGNMENT);
            Runnable refreshFloatState=()->{
                boolean fl=o.density<sim.fluidDensity;
                fi.setText(fl?"▲ FLOATS":"▼ SINKS");
                fi.setForeground(fl?new Color(80,220,120):new Color(220,80,80));
            };
            refreshFloatState.run();
            floatStateUpdaters.add(refreshFloatState);
            ds.addChangeListener(e->refreshFloatState.run());
            card.add(fi);

            card.add(vg(4));
            card.add(clbl("Shape",OBJ_COLS[i]));
            JComboBox<String> shapeCombo=new JComboBox<>(shapeNames);
            styleCombo(shapeCombo);
            shapeCombo.setSelectedIndex(Math.max(0,Math.min(shapeNames.length-1,o.shape)));
            shapeCombo.addActionListener(e->o.shape=shapeCombo.getSelectedIndex());
            card.add(shapeCombo);

            card.add(vg(4));
            card.add(clbl("Drift Bias",OBJ_COLS[i]));
            JSlider driftSlider=slider2(-180,180,(int)o.driftBias,OBJ_COLS[i]);
            JLabel driftVal=valLbl(String.format("%+.0f px/s",o.driftBias));
            driftSlider.addChangeListener(e->{
                o.driftBias=driftSlider.getValue();
                driftVal.setText(String.format("%+.0f px/s",o.driftBias));
                if(!o.pinned&&Math.abs(o.vx)<5)o.vx=o.driftBias;
            });
            card.add(driftSlider);card.add(driftVal);
            p.add(card);p.add(vg(7));
        }

        // ── FLUID card ──
        JPanel flCard=card2("FLUID",new Color(60,160,255));
        flCard.add(clbl("Fluid Type",new Color(120,180,255)));
        String[]fTypes={"Water","Oil","Mercury","Salt Water","Petrol"};
        JComboBox<String> flCombo=new JComboBox<>(fTypes);
        styleCombo(flCombo);
        flCombo.setSelectedIndex(0);
        flCard.add(flCombo);flCard.add(vg(5));

        flCard.add(clbl("Fluid Density",new Color(120,180,255)));
        JSlider fdSlider=slider2(100,14000,(int)sim.fluidDensity,new Color(60,160,255));
        JLabel fdVal=valLbl("1000 kg/m³");
        fdSlider.addChangeListener(e->{sim.fluidDensity=fdSlider.getValue();fdVal.setText(fdSlider.getValue()+" kg/m³");for(Runnable up:floatStateUpdaters)up.run();});
        flCard.add(fdSlider);flCard.add(fdVal);flCard.add(vg(5));

        flCard.add(clbl("Viscosity",new Color(120,180,255)));
        JSlider visSlider=slider2(1,200,(int)Math.round(sim.viscosity*1000),new Color(80,180,255));
        JLabel visVal=valLbl("0.020");
        visSlider.addChangeListener(e->{sim.viscosity=visSlider.getValue()/1000.0;visVal.setText(String.format("%.3f",sim.viscosity));});
        flCard.add(visSlider);flCard.add(visVal);flCard.add(vg(5));

        flCard.add(clbl("Surface Tension",new Color(120,180,255)));
        JSlider stSlider=slider2(0,100,(int)Math.round(sim.surfaceTension*100),new Color(110,190,250));
        JLabel stVal=valLbl(String.format("%.2f",sim.surfaceTension));
        stSlider.addChangeListener(e->{sim.surfaceTension=stSlider.getValue()/100.0;stVal.setText(String.format("%.2f",sim.surfaceTension));});
        flCard.add(stSlider);flCard.add(stVal);flCard.add(vg(5));

        flCard.add(clbl("Current Strength",new Color(120,180,255)));
        JSlider curSlider=slider2(-180,180,(int)Math.round(sim.currentStrength),new Color(95,205,255));
        JLabel curVal=valLbl(String.format("%+.0f px/s",sim.currentStrength));
        curSlider.addChangeListener(e->{sim.currentStrength=curSlider.getValue();curVal.setText(String.format("%+.0f px/s",sim.currentStrength));});
        flCard.add(curSlider);flCard.add(curVal);flCard.add(vg(5));

        flCard.add(clbl("Gravity Scale",new Color(120,180,255)));
        JSlider gSlider=slider2(20,220,(int)Math.round(sim.gravityScale*100),new Color(130,190,255));
        JLabel gVal=valLbl(String.format("%.2fx",sim.gravityScale));
        gSlider.addChangeListener(e->{sim.gravityScale=gSlider.getValue()/100.0;gVal.setText(String.format("%.2fx",sim.gravityScale));});
        flCard.add(gSlider);flCard.add(gVal);flCard.add(vg(5));

        flCard.add(clbl("Water Level",new Color(120,180,255)));
        JSlider wlSlider=slider2(5,95,(int)Math.round(sim.waterLevel*100),new Color(60,200,255));
        JLabel wlVal=valLbl(String.format("%.0f%%",sim.waterLevel*100));
        wlSlider.addChangeListener(e->{sim.waterLevel=wlSlider.getValue()/100.0;wlVal.setText(wlSlider.getValue()+"%");});
        flCard.add(wlSlider);flCard.add(wlVal);

        flCombo.addActionListener(e->{
            sim.setFluidType(flCombo.getSelectedIndex());
            fdSlider.setValue((int)Math.round(sim.fluidDensity));
            visSlider.setValue((int)Math.round(sim.viscosity*1000));
            stSlider.setValue((int)Math.round(sim.surfaceTension*100));
            sim.startSinkAnimation();
            for(Runnable up:floatStateUpdaters)up.run();
        });
        p.add(flCard);p.add(vg(7));

        // ── WAVE card ──
        JPanel wvCard=card2("WAVES & TURBULENCE",new Color(80,200,200));
        JToggleButton waveBtn=togBtn("Wave Mode OFF",false,new Color(18,42,78));
        waveBtn.addActionListener(e->{sim.waveMode=waveBtn.isSelected();waveBtn.setText(sim.waveMode?"Wave Mode ON":"Wave Mode OFF");});
        wvCard.add(waveBtn);wvCard.add(vg(5));

        wvCard.add(clbl("Wave Amplitude",new Color(80,200,200)));
        JSlider waSlider=slider2(0,80,18,new Color(80,200,200));
        JLabel waVal=valLbl("18 px");
        waSlider.addChangeListener(e->{sim.waveAmp=waSlider.getValue();waVal.setText(waSlider.getValue()+" px");});
        wvCard.add(waSlider);wvCard.add(waVal);wvCard.add(vg(5));

        wvCard.add(clbl("Wave Frequency",new Color(80,200,200)));
        JSlider wfSlider=slider2(1,40,12,new Color(60,180,200));
        JLabel wfVal=valLbl("1.2 Hz");
        wfSlider.addChangeListener(e->{sim.waveFreq=wfSlider.getValue()/10.0;wfVal.setText(String.format("%.1f Hz",sim.waveFreq));});
        wvCard.add(wfSlider);wvCard.add(wfVal);wvCard.add(vg(5));

        JToggleButton turbBtn=togBtn("Turbulence OFF",false,new Color(18,42,78));
        turbBtn.addActionListener(e->{sim.turbulence=turbBtn.isSelected();turbBtn.setText(sim.turbulence?"Turbulence ON":"Turbulence OFF");});
        wvCard.add(turbBtn);wvCard.add(vg(5));

        wvCard.add(clbl("Turbulence Intensity",new Color(80,200,200)));
        JSlider tiSlider=slider2(0,100,0,new Color(100,200,180));
        JLabel tiVal=valLbl("0%");
        tiSlider.addChangeListener(e->{sim.turbAmp=tiSlider.getValue()/100.0;tiVal.setText(tiSlider.getValue()+"%");});
        wvCard.add(tiSlider);wvCard.add(tiVal);
        p.add(wvCard);p.add(vg(8));

        // ── Simulation card ──
        JPanel simCard=card2("SIMULATION",new Color(240,190,110));
        JToggleButton pauseBtn=togBtn("Pause Physics",sim.paused,new Color(35,42,70));
        pauseBtn.addActionListener(e->{sim.paused=pauseBtn.isSelected();pauseBtn.setText(sim.paused?"Resume Physics":"Pause Physics");});
        simCard.add(pauseBtn);simCard.add(vg(5));

        simCard.add(clbl("Time Scale",new Color(240,190,110)));
        JSlider tsSlider=slider2(20,300,(int)Math.round(sim.timeScale*100),new Color(240,190,110));
        JLabel tsVal=valLbl(String.format("%.2fx",sim.timeScale));
        tsSlider.addChangeListener(e->{sim.timeScale=tsSlider.getValue()/100.0;tsVal.setText(String.format("%.2fx",sim.timeScale));});
        simCard.add(tsSlider);simCard.add(tsVal);
        p.add(simCard);p.add(vg(7));

        // ── Display card ──
        JPanel dCard=card2("DISPLAY",new Color(165,205,255));
        JToggleButton trailBtn=togBtn("Trails",sim.showTrails,new Color(18,42,78));
        JToggleButton forceBtn=togBtn("Force Arrows",sim.showForceArrows,new Color(18,42,78));
        JToggleButton labelBtn=togBtn("Labels",sim.showLabels,new Color(18,42,78));
        JToggleButton bubbleBtn=togBtn("Bubbles",sim.showBubbles,new Color(18,42,78));
        JToggleButton flowBtn=togBtn("Flow Field",sim.showFlowField,new Color(18,42,78));
        trailBtn.addActionListener(e->sim.showTrails=trailBtn.isSelected());
        forceBtn.addActionListener(e->sim.showForceArrows=forceBtn.isSelected());
        labelBtn.addActionListener(e->sim.showLabels=labelBtn.isSelected());
        bubbleBtn.addActionListener(e->sim.showBubbles=bubbleBtn.isSelected());
        flowBtn.addActionListener(e->sim.showFlowField=flowBtn.isSelected());
        dCard.add(trailBtn);dCard.add(vg(4));
        dCard.add(forceBtn);dCard.add(vg(4));
        dCard.add(labelBtn);dCard.add(vg(4));
        dCard.add(bubbleBtn);dCard.add(vg(4));
        dCard.add(flowBtn);
        p.add(dCard);p.add(vg(8));

        // action buttons
        JButton resetBtn=bigBtn("↺  RESET",new Color(20,40,88),new Color(100,160,240));
        resetBtn.addActionListener(e->sim.startSinkAnimation());
        JButton dropBtn=bigBtn("⇩  DROP TEST",new Color(16,50,95),new Color(105,185,250));
        dropBtn.addActionListener(e->sim.dropObjects());
        JButton stirBtn=bigBtn("≈  STIR FLUID",new Color(18,58,92),new Color(110,215,255));
        stirBtn.addActionListener(e->sim.stirFluid());
        JButton randBtn=bigBtn("⚗  RANDOMIZE",new Color(42,56,96),new Color(255,210,120));
        randBtn.addActionListener(e->sim.randomizeScene());
        p.add(resetBtn);
        p.add(vg(5));
        p.add(dropBtn);
        p.add(vg(5));
        p.add(stirBtn);
        p.add(vg(5));
        p.add(randBtn);

        if(onBack!=null){
            p.add(vg(6));
            JButton backBtn=bigBtn("← HOME",new Color(10,20,50),new Color(80,130,210));
            backBtn.addActionListener(e->{setVisible(false);dispose();if(onBack!=null)SwingUtilities.invokeLater(onBack);});
            p.add(backBtn);
        }

        JScrollPane scroll=new JScrollPane(p);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        scroll.getViewport().setOpaque(false);scroll.setOpaque(false);
        outer.add(scroll,BorderLayout.CENTER);
        return outer;
    }

    // ── AI chat panel ────────────────────────────────────────────────────────
    JPanel buildChatPanel(){
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(new Color(5,8,20));
        outer.setBorder(BorderFactory.createMatteBorder(1,0,0,0,new Color(40,65,130)));
        chatPanelRef = outer;

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(9,15,35));
        header.setBorder(BorderFactory.createEmptyBorder(6,14,6,14));
        JLabel title = new JLabel("  AI ASSISTANT  —  Ask anything about buoyancy and fluids");
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

        chatDisplay = new JTextArea();
        chatDisplay.setEditable(false);
        chatDisplay.setLineWrap(true);
        chatDisplay.setWrapStyleWord(true);
        chatDisplay.setBackground(new Color(7,11,25));
        chatDisplay.setForeground(new Color(210,225,250));
        chatDisplay.setFont(new Font("Segoe UI",Font.PLAIN,13));
        chatDisplay.setBorder(BorderFactory.createEmptyBorder(10,14,10,14));
        chatDisplay.setText("Assistant: Hello! I'm here to help you understand this Buoyancy Physics Engine.\n"
            + "Ask me about: Archimedes' principle, density vs fluid density, drag, surface tension, waves,\n"
            + "turbulence, and why objects float, sink, or oscillate in this simulation.\n");

        JScrollPane chatScroll = new JScrollPane(chatDisplay);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());
        chatScroll.setBackground(new Color(7,11,25));
        chatScroll.setPreferredSize(new Dimension(0,88));
        chatScroll.getVerticalScrollBar().setBackground(new Color(12,20,44));

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
    // Shared API key configured in SolarSystemEngine for consistent behavior.
    static final String NVIDIA_API_KEY = SolarSystemEngine.NVIDIA_API_KEY;

    static final String SYSTEM_PROMPT =
        "You are an expert assistant embedded inside a Java Swing Buoyancy Physics Engine simulation. " +
        "Answer questions about Archimedes' principle, density, buoyant force, drag, surface tension, currents, turbulence, and wave behavior. " +
        "Explain how the controls affect object motion using concise technical language. Keep answers under 3 sentences. " +
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
        i++;

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

    // ── helpers ───────────────────────────────────────────────────────────
    Component vg(int h){return javax.swing.Box.createVerticalStrut(h);}
    JLabel lbl(String t,Font f,Color c){JLabel l=new JLabel(t);l.setFont(f);l.setForeground(c);return l;}
    JLabel valLbl(String t){JLabel l=new JLabel(t);l.setFont(new Font("Segoe UI",Font.BOLD,11));l.setForeground(new Color(210,195,120));l.setAlignmentX(LEFT_ALIGNMENT);return l;}
    JLabel clbl(String t,Color c){JLabel l=new JLabel(t);l.setFont(new Font("Segoe UI",Font.BOLD,11));l.setForeground(new Color(c.getRed(),c.getGreen(),c.getBlue(),190));l.setAlignmentX(LEFT_ALIGNMENT);l.setBorder(BorderFactory.createEmptyBorder(2,0,1,0));return l;}

    JPanel row2(){JPanel r=new JPanel(new BorderLayout(6,0));r.setBackground(new Color(0,0,0,0));r.setMaximumSize(new Dimension(Integer.MAX_VALUE,28));return r;}

    JPanel card2(String title,Color accent){
        JPanel c=new JPanel(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                RoundRectangle2D box=new RoundRectangle2D.Double(.5,.5,getWidth()-1,getHeight()-1,16,16);
                g2.setPaint(new GradientPaint(0,0,CARD_T,0,getHeight(),CARD_B));g2.fill(box);
                g2.setColor(new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),120));
                g2.fillRoundRect(0,0,4,getHeight(),4,4);
                g2.setColor(CARD_EDGE);g2.draw(box);
                g2.dispose();
            }
        };
        c.setOpaque(false);c.setLayout(new BoxLayout(c,BoxLayout.Y_AXIS));
        c.setAlignmentX(CENTER_ALIGNMENT);c.setMaximumSize(new Dimension(Integer.MAX_VALUE,2000));
        c.setBorder(BorderFactory.createEmptyBorder(8,10,10,10));
        JLabel tl=new JLabel(title,SwingConstants.CENTER);
        tl.setFont(new Font("Segoe UI",Font.BOLD,12));
        tl.setForeground(new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),220));
        tl.setAlignmentX(CENTER_ALIGNMENT);tl.setBorder(BorderFactory.createEmptyBorder(0,0,5,0));
        c.add(tl);return c;
    }

    JSlider slider2(int mn,int mx,int v,Color tint){
        JSlider s=new JSlider(mn,mx,Math.max(mn,Math.min(mx,v)));
        s.setBackground(new Color(0,0,0,0));s.setOpaque(false);
        s.setForeground(tint);s.setMaximumSize(new Dimension(Integer.MAX_VALUE,26));
        return s;
    }

    JToggleButton togBtn(String text,boolean sel,Color bg){
        JToggleButton b=new JToggleButton(text,sel){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                Color base=isSelected()?new Color(bg.getRed()+28,bg.getGreen()+28,bg.getBlue()+38,215):new Color(bg.getRed(),bg.getGreen(),bg.getBlue(),155);
                g2.setPaint(new GradientPaint(0,0,base.brighter(),0,getHeight(),base));
                g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);
                if(isSelected()){g2.setColor(new Color(80,150,240,100));g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);}
                g2.dispose();super.paintComponent(g);
            }
        };
        b.setFont(new Font("Segoe UI",Font.BOLD,11));b.setForeground(sel?TEXT_PRI:new Color(155,172,208));
        b.setContentAreaFilled(false);b.setOpaque(false);b.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
        b.setFocusPainted(false);b.setMaximumSize(new Dimension(Integer.MAX_VALUE,32));b.setAlignmentX(CENTER_ALIGNMENT);
        b.addItemListener(e->b.setForeground(b.isSelected()?TEXT_PRI:new Color(155,172,208)));
        return b;
    }

    JButton bigBtn(String text,Color bg,Color fg){
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
        b.setForeground(fg);b.setFont(new Font("Segoe UI",Font.BOLD,13));
        b.setContentAreaFilled(false);b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(9,14,9,14));
        b.setFocusPainted(false);b.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));
        b.setAlignmentX(CENTER_ALIGNMENT);b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    void styleCombo(JComboBox<?> c){
        c.setBackground(new Color(18,32,65));c.setForeground(TEXT_PRI);c.setFont(new Font("Segoe UI",Font.BOLD,11));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));
        c.setRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(JList<?> lst,Object val,int idx,boolean sel,boolean foc){
                JLabel l=(JLabel)super.getListCellRendererComponent(lst,val,idx,sel,foc);
                l.setFont(new Font("Segoe UI",Font.BOLD,11));l.setBorder(BorderFactory.createEmptyBorder(3,7,3,7));
                l.setBackground(sel?new Color(40,80,140):new Color(18,32,65));l.setForeground(TEXT_PRI);return l;
            }
        });
    }
}