import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class BuoyancyEngine extends JFrame {

    static final int W = 1200, H = 800;
    static final int CTRL_W = 320;
    static final double G_ACCEL = 400.0;

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
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle scr = ge.getMaximumWindowBounds();
        setSize(Math.min(W, scr.width), Math.min(H, scr.height));
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  FLOATING OBJECT
    // ════════════════════════════════════════════════════════════════════════
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

        double displacedVolume() {
            // Use physical volume from mass and density for buoyancy force consistency.
            return mass / Math.max(1e-6, density);
        }

        // submerged fraction [0,1]
        double submergedFraction(double waterSurfaceY, double waterBottomY) {
            double top    = y - height / 2.0;
            double bottom = y + height / 2.0;
            double waterY = waterSurfaceY;
            if (bottom <= waterY) return 0.0;            // fully above
            if (top >= waterBottomY) return 1.0;         // fully below water
            double subH = Math.min(bottom, waterBottomY) - Math.max(top, waterY);
            return Math.max(0, Math.min(1, subH / height));
        }

        Rectangle2D.Double rect() {
            return new Rectangle2D.Double(x-width/2*squishX, y-height/2*squishY, width*squishX, height*squishY);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  BUBBLE / SPLASH PARTICLE
    // ════════════════════════════════════════════════════════════════════════
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
    class SimPanel extends JPanel {
        FloatObj[] objects;
        List<Bubble> bubbles = new ArrayList<>();

        double fluidDensity = 1000.0;  // water = 1000 kg/m³
        double viscosity    = 0.02;    // drag coefficient
        double surfaceTension = 0.3;   // subtle surface cling
        boolean turbulence  = false;
        double turbAmp      = 0.0;
        boolean waveMode    = false;
        double waveTime     = 0;
        double waveAmp      = 18.0;
        double waveFreq     = 1.2;

        // tank
        int tankMargin = 60;
        double waterLevel = 0.72;  // fraction of tank height filled with water
        double waterSurfY;         // computed each frame
        double waterShake = 0;     // horizontal wave offset

        // stars
        double[] stX, stY, stR, stB;

        javax.swing.Timer loop;
        long lastMs = System.currentTimeMillis();

        // hover drag
        int dragObjIdx = -1;
        Point lastDrag = null;

        SimPanel() {
            setBackground(BG_DEEP); setOpaque(true);
            Random rng = new Random(13);
            int ns=200; stX=new double[ns];stY=new double[ns];stR=new double[ns];stB=new double[ns];
            for(int i=0;i<ns;i++){stX[i]=rng.nextDouble();stY[i]=rng.nextDouble();stR[i]=rng.nextDouble()*1.3+0.3;stB[i]=rng.nextDouble()*0.5+0.2;}

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
            resetPositions();

            addMouseListener(new MouseAdapter(){
                public void mousePressed(MouseEvent e){
                    dragObjIdx=-1;
                    for(int i=0;i<objects.length;i++){
                        FloatObj o=objects[i]; if(!o.active)continue;
                        if(o.rect().contains(e.getX(),e.getY())){ dragObjIdx=i; lastDrag=e.getPoint(); o.pinned=true; break;}
                    }
                }
                public void mouseReleased(MouseEvent e){ if(dragObjIdx>=0){objects[dragObjIdx].pinned=false; objects[dragObjIdx].vx=objects[dragObjIdx].vy=0;} dragObjIdx=-1; lastDrag=null; }
            });
            addMouseMotionListener(new MouseMotionAdapter(){
                public void mouseDragged(MouseEvent e){
                    if(dragObjIdx>=0&&lastDrag!=null){
                        FloatObj o=objects[dragObjIdx];
                        int dx = e.getX() - lastDrag.x;
                        int dy = e.getY() - lastDrag.y;
                        o.x += dx; o.y += dy;
                        waterShake += dx * 0.04;
                        lastDrag = e.getPoint();
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
            int pw=getWidth()>10?getWidth():W-CTRL_W;
            int ph=getHeight()>10?getHeight():H;
            int tankLeft=tankMargin, tankW=pw-tankMargin*2;
            int[] xs={tankLeft+tankW/5, tankLeft+2*tankW/5, tankLeft+3*tankW/5, tankLeft+4*tankW/5};
            double tankH=ph-tankMargin*2;
            double surfY=ph-tankMargin-tankH*waterLevel;
            for(int i=0;i<objects.length;i++){
                FloatObj o=objects[i];
                o.x=xs[i]; o.y=surfY-o.height*0.5-20;
                o.vx=o.vy=0; o.trail.clear(); o.squishX=o.squishY=1;
            }
        }

        void setFluidType(int type){
            switch(type){
                case 0: fluidDensity=1000;viscosity=0.02;break; // water
                case 1: fluidDensity=800; viscosity=0.03;break; // oil
                case 2: fluidDensity=13600;viscosity=0.06;break; // mercury
                case 3: fluidDensity=1200;viscosity=0.04;break; // saltwater
                case 4: fluidDensity=680; viscosity=0.008;break;// petrol
            }
        }

        // ── physics ───────────────────────────────────────────────────────
        void tick(double dt) {
            int pw=getWidth()>10?getWidth():W-CTRL_W;
            int ph=getHeight()>10?getHeight():H;
            int tankLeft=tankMargin, tankRight=pw-tankMargin;
            int tankTop=tankMargin, tankBottom=ph-tankMargin;
            double tankH=tankBottom-tankTop;

            waveTime+=dt;
            waterShake*=0.92;
            double waveOffset = waveMode ? waveAmp*Math.sin(waveTime*waveFreq*Math.PI*2) : 0;
            waterSurfY = tankBottom - tankH*waterLevel + waveOffset;

            bubbles.removeIf(b->b.tick(dt, G_ACCEL));

            for(FloatObj o:objects){
                if(!o.active||o.pinned)continue;

                double frac = o.submergedFraction(waterSurfY, tankBottom);
                double vol  = o.displacedVolume();

                // buoyancy: F = rho_fluid * g * V_submerged
                double Fb = fluidDensity * G_ACCEL * vol * frac;

                // gravity: F = m * g
                double Fg = o.mass * G_ACCEL;

                // net vertical force
                double Fnet = Fb - Fg;

                // viscous drag (proportional to velocity, stronger when submerged)
                double dragH = viscosity * (1 + frac * 2.8);
                double dragV = viscosity * (1 + frac * 3.5);

                o.vx *= (1 - dragH * dt * 6);
                o.vy += (Fnet / o.mass) * dt;
                o.vy *= (1 - dragV * dt * 5.5);

                // surface tension: small snap near waterline
                double centreRelSurf = o.y - waterSurfY;
                if(Math.abs(centreRelSurf) < o.height*0.6 && frac>0.05 && frac<0.95){
                    o.vy -= centreRelSurf * surfaceTension * dt * 0.5;
                }

                // turbulence
                if(turbulence && turbAmp>0){
                    o.vx += (Math.random()-0.5)*turbAmp*dt*80*frac;
                    o.vy += (Math.random()-0.5)*turbAmp*dt*40*frac;
                }

                o.x += o.vx*dt;
                o.y += o.vy*dt;

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
                if(o.splashTimer>0){o.splashTimer-=dt;if(o.splashTimer<=0){o.squishX=1;o.squishY=1;}}

                // bubble generation when submerged fast
                if(frac>0.5 && Math.abs(o.vy)>20 && Math.random()<0.25)
                    bubbles.add(new Bubble(o.x+(Math.random()-0.5)*o.width*0.7,o.y-o.height*0.3,false));

                // recovery squish
                o.squishX+=(1-o.squishX)*0.15;
                o.squishY+=(1-o.squishY)*0.15;

                // tank walls
                double halfW=o.width/2, halfH=o.height/2;
                if(o.x-halfW<tankLeft){o.x=tankLeft+halfW;o.vx=Math.abs(o.vx)*0.4;}
                if(o.x+halfW>tankRight){o.x=tankRight-halfW;o.vx=-Math.abs(o.vx)*0.4;}
                if(o.y+halfH>tankBottom){o.y=tankBottom-halfH;o.vy=-Math.abs(o.vy)*0.3;o.vx*=0.7;spawnSplash((int)o.x,(int)o.y,o.col,4);}
                if(o.y-halfH<tankTop){o.y=tankTop+halfH;o.vy=Math.abs(o.vy)*0.3;}

                // trail
                o.trail.add(new Point2D.Double(o.x,o.y));
                if(o.trail.size()>60)o.trail.remove(0);

                // ambient bubbles underwater
                if(frac>0.8&&Math.random()<0.04)
                    bubbles.add(new Bubble(o.x+(Math.random()-0.5)*o.width*0.6,o.y+o.height*0.4,false));
            }

            // object-object collisions in water
            FloatObj[] active = Arrays.stream(objects).filter(o->o.active&&!o.pinned).toArray(FloatObj[]::new);
            for(int i=0;i<active.length;i++)for(int j=i+1;j<active.length;j++) resolveBoxCollision(active[i],active[j]);

            // passive ambient bubbles rising
            if(Math.random()<0.06&&waterLevel>0.1)
                bubbles.add(new Bubble(tankLeft+(Math.random()*(tankRight-tankLeft)),(int)(waterSurfY+tankH*0.6),false));
        }

        void spawnSplash(int x,int y,Color c,int n){for(int i=0;i<n;i++)bubbles.add(new Bubble(x,y,true));}

        void resolveBoxCollision(FloatObj a, FloatObj b){
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
            drawTrails(g2);
            for(Bubble b:bubbles)b.draw(g2);
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
            GradientPaint waterFill=new GradientPaint(tl,surfY,WATER_TOP,tl,tb,WATER_BOT);
            g2.setPaint(waterFill); g2.fill(wave);

            // caustic light beams (animated)
            double ct=waveTime*0.8;
            for(int i=0;i<8;i++){
                double cx2=tl+(tr-tl)*((i+0.5)/8.0+Math.sin(ct+i*1.1)*0.05);
                double cw=12+8*Math.sin(ct*1.3+i);
                float ca=(float)(0.03+0.02*Math.sin(ct+i));
                GradientPaint caustic=new GradientPaint((float)cx2,(float)surfY,new Color(180,230,255,(int)(ca*255)),
                    (float)cx2,(float)tb,new Color(60,140,220,0));
                g2.setPaint(caustic);
                g2.fillRect((int)(cx2-cw/2),(int)surfY,(int)cw,(int)(tb-surfY));
            }

            // subsurface depth fog
            GradientPaint depthFog=new GradientPaint(tl,surfY,new Color(20,80,160,0),tl,tb,new Color(5,25,80,80));
            g2.setPaint(depthFog); g2.fill(wave);

            g2.setClip(oldClip);

            // surface line glow
            g2.setStroke(new BasicStroke(1.8f));
            g2.setColor(WATER_SURF);
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
            // clip to tank
            int tl=tankMargin,tr=pw-tankMargin,tt=tankMargin,tb=ph-tankMargin;
            Shape oldClip=g2.getClip();
            g2.setClip(new RoundRectangle2D.Double(tl+2,tt+2,tr-tl-4,tb-tt-4,16,16));

            for(FloatObj o:objects){
                if(!o.active)continue;
                double frac=o.submergedFraction(waterSurfY,tb);
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

                // box fill
                GradientPaint fill=new GradientPaint((float)bx,(float)by2,
                    new Color(Math.min(255,o.col.getRed()+55),Math.min(255,o.col.getGreen()+40),Math.min(255,o.col.getBlue()+35)),
                    (float)bx,(float)(by2+bh),new Color(o.col.getRed()/2,o.col.getGreen()/2,o.col.getBlue()/2));
                g2.setPaint(fill);
                g2.fillRoundRect((int)bx,(int)by2,(int)bw,(int)bh,10,10);

                // underwater desaturation + blue tint
                if(frac>0){
                    int ua=(int)(frac*75);
                    g2.setColor(new Color(20,80,150,ua));
                    g2.fillRoundRect((int)bx,(int)by2,(int)bw,(int)bh,10,10);
                }

                // rim highlight
                g2.setColor(new Color(255,255,255,frac<0.5?60:28));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawLine((int)bx+6,(int)by2+2,(int)(bx+bw-6),(int)by2+2);

                // border
                g2.setColor(new Color(o.col.getRed(),o.col.getGreen(),o.col.getBlue(),frac>0.5?140:190));
                g2.setStroke(new BasicStroke(1.8f));
                g2.drawRoundRect((int)bx,(int)by2,(int)bw,(int)bh,10,10);
                g2.setStroke(new BasicStroke(1));

                // label
                g2.setFont(new Font("Segoe UI",Font.BOLD,12));
                g2.setColor(new Color(255,255,255,frac>0.8?140:200));
                FontMetrics fm=g2.getFontMetrics();
                g2.drawString(o.label,(int)(o.x-fm.stringWidth(o.label)/2.0),(int)(o.y+4));

                // buoyancy force arrow
                double fb=fluidDensity*G_ACCEL*o.displacedVolume()*frac;
                double fg=o.mass*G_ACCEL;
                double net=fb-fg;
                if(Math.abs(net)>2){
                    double arrowLen=Math.min(Math.abs(net)*0.08,70);
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
                    // net force label
                    g2.setFont(new Font("Segoe UI",Font.PLAIN,9));
                    g2.setColor(new Color(ac.getRed(),ac.getGreen(),ac.getBlue(),180));
                    g2.drawString(String.format("%+.0fN",net),(int)(ex+4),(int)((ay+ey)/2.0));
                }

                // density readout
                g2.setFont(new Font("Segoe UI",Font.PLAIN,10));
                g2.setColor(new Color(220,235,255,frac>0.5?110:150));
                String dl=String.format("%.0f kg/m³",o.density);
                fm=g2.getFontMetrics();
                g2.drawString(dl,(int)(o.x-fm.stringWidth(dl)/2),(int)(o.y+o.height/2*o.squishY+13));
            }

            g2.setClip(oldClip);
        }

        void drawHUD(Graphics2D g2,int pw){
            // fluid info top-left
            int hx=tankMargin+8,hy=tankMargin+8;
            String fName;
            if(fluidDensity<=700)fName="Petrol";
            else if(fluidDensity<=850)fName="Oil";
            else if(fluidDensity<=1050)fName=waterLevel>0.3?"Water":"Shallow Water";
            else if(fluidDensity<=1250)fName="Salt Water";
            else fName="Mercury";
            g2.setColor(new Color(8,14,32,185));g2.fillRoundRect(hx,hy,160,50,10,10);
            g2.setColor(new Color(50,90,180,90));g2.drawRoundRect(hx,hy,160,50,10,10);
            g2.setFont(new Font("Segoe UI",Font.BOLD,11));g2.setColor(new Color(120,180,255,220));
            g2.drawString("FLUID: "+fName,hx+10,hy+16);
            g2.setFont(new Font("Segoe UI",Font.PLAIN,10));g2.setColor(new Color(160,195,240,185));
            g2.drawString(String.format("ρ=%.0f kg/m³   η=%.3f",fluidDensity,viscosity),hx+10,hy+30);
            g2.drawString(String.format("Water Level: %.0f%%",waterLevel*100),hx+10,hy+43);

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
        for(int i=0;i<4;i++){
            FloatObj o=sim.objects[i];
            JPanel card=card2("OBJ "+labels[i].split(" ")[1],OBJ_COLS[i]);

            // active toggle
            JPanel hdr=row2(); hdr.setMaximumSize(new Dimension(Integer.MAX_VALUE,28));
            JLabel tit=lbl(labels[i].toUpperCase(),new Font("Segoe UI",Font.BOLD,12),OBJ_COLS[i]);
            JToggleButton actBtn=togBtn("ON",o.active,OBJ_COLS[i].darker().darker());
            actBtn.addActionListener(e->{o.active=actBtn.isSelected();actBtn.setText(o.active?"ON":"OFF");sim.resetPositions();});
            hdr.add(tit,BorderLayout.WEST);hdr.add(actBtn,BorderLayout.EAST);
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
            ds.addChangeListener(e->{boolean fl=ds.getValue()<(int)sim.fluidDensity;fi.setText(fl?"▲ FLOATS":"▼ SINKS");fi.setForeground(fl?new Color(80,220,120):new Color(220,80,80));});
            card.add(fi);
            p.add(card);p.add(vg(7));
        }

        // ── FLUID card ──
        JPanel flCard=card2("FLUID",new Color(60,160,255));
        flCard.add(clbl("Fluid Type",new Color(120,180,255)));
        String[]fTypes={"Water","Oil","Mercury","Salt Water","Petrol"};
        JComboBox<String> flCombo=new JComboBox<>(fTypes);
        styleCombo(flCombo);
        flCombo.addActionListener(e->sim.setFluidType(flCombo.getSelectedIndex()));
        flCard.add(flCombo);flCard.add(vg(5));

        flCard.add(clbl("Fluid Density",new Color(120,180,255)));
        JSlider fdSlider=slider2(100,14000,1000,new Color(60,160,255));
        JLabel fdVal=valLbl("1000 kg/m³");
        fdSlider.addChangeListener(e->{sim.fluidDensity=fdSlider.getValue();fdVal.setText(fdSlider.getValue()+" kg/m³");});
        flCombo.addActionListener(e->{int[]dens={1000,800,13600,1200,680};int fi2=flCombo.getSelectedIndex();fdSlider.setValue(dens[fi2]);});
        flCard.add(fdSlider);flCard.add(fdVal);flCard.add(vg(5));

        flCard.add(clbl("Viscosity",new Color(120,180,255)));
        JSlider visSlider=slider2(1,200,20,new Color(80,180,255));
        JLabel visVal=valLbl("0.020");
        visSlider.addChangeListener(e->{sim.viscosity=visSlider.getValue()/1000.0;visVal.setText(String.format("%.3f",sim.viscosity));});
        flCard.add(visSlider);flCard.add(visVal);flCard.add(vg(5));

        flCard.add(clbl("Water Level",new Color(120,180,255)));
        JSlider wlSlider=slider2(5,95,72,new Color(60,200,255));
        JLabel wlVal=valLbl("72%");
        wlSlider.addChangeListener(e->{sim.waterLevel=wlSlider.getValue()/100.0;wlVal.setText(wlSlider.getValue()+"%");});
        flCard.add(wlSlider);flCard.add(wlVal);
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

        // action buttons
        JButton resetBtn=bigBtn("↺  RESET",new Color(20,40,88),new Color(100,160,240));
        resetBtn.addActionListener(e->sim.resetPositions());
        p.add(resetBtn);

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

    // ── helpers ───────────────────────────────────────────────────────────
    Component vg(int h){return Box.createVerticalStrut(h);}
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
