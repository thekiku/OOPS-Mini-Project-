import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class CollisionEngine extends JFrame {

    // ─── constants ────────────────────────────────────────────────────────────
    static final int W = 1280, H = 820, CTRL_W = 300;
    static final double GRAVITY = 500.0;

    // ─── palette ──────────────────────────────────────────────────────────────
    static final Color BG0   = new Color(4,   5,  14);
    static final Color BG1   = new Color(8,  10,  24);
    static final Color PANEL = new Color(6,   8,  20);
    static final Color CARD0 = new Color(12, 18,  42, 235);
    static final Color CARD1 = new Color( 7, 12,  30, 235);
    static final Color CEDGE = new Color(50, 75, 180,  70);
    static final Color TXT0  = new Color(225, 235, 255);
    static final Color TXT1  = new Color(140, 158, 210);
    static final Color GOLD  = new Color(255, 205, 100);
    static final Color GRID  = new Color(20,  35,  85,  38);

    static final Color[] BOX_CLR = {
        new Color(235, 65,  60),
        new Color( 50, 195,  85),
        new Color( 50, 140, 240),
    };
    static final String[] BOX_NM = {"RED", "GRN", "BLU"};

    // ─── state ────────────────────────────────────────────────────────────────
    SimPanel sim;
    Runnable onBack;
    JTextArea chatDisplay;
    JTextField chatInput;
    boolean chatCollapsed = true;
    JPanel chatPanelRef;

    public static void main(String[] a){ SwingUtilities.invokeLater(CollisionEngine::new); }
    public CollisionEngine(){ this(null); }
    public CollisionEngine(Runnable cb){
        onBack = cb;
        setTitle("Collision Physics Engine");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG0);
        sim = new SimPanel();
        add(sim, BorderLayout.CENTER);
        add(buildCtrl(), BorderLayout.EAST);
        add(buildChatPanel(), BorderLayout.SOUTH);
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(W, scr.width), Math.min(H, scr.height));
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHYSICS BODY
    // ══════════════════════════════════════════════════════════════════════════
    static class Body {
        int   id;
        double x, y, vx, vy, mass;
        double initVel = 180;
        int    dir     = 0;          // 0=→ 1=← 2=↑ 3=↓
        boolean active = true;
        double launchVx = 0, launchVy = 0;
        boolean customLaunch = false;
        Color  col;
        // visual
        double sx = 1, sy = 1;      // squash/stretch
        double glow = 0;
        List<double[]> trail = new ArrayList<>();

        Body(int id, Color col){ this.id=id; this.col=col; mass=25; }

        double side(){ return 28 + 52*(1 - 1.0/(1 + mass/35.0)); }

        double left(){ return x - side()/2*sx; }
        double top (){ return y - side()/2*sy; }
        double right(){ return x + side()/2*sx; }
        double bot  (){ return y + side()/2*sy; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SPARK PARTICLE
    // ══════════════════════════════════════════════════════════════════════════
    static class Spark {
        double x, y, vx, vy, life, maxLife, sz;
        Color c;
        Spark(double x, double y, Color c){
            this.x=x; this.y=y; this.c=c;
            double a=Math.random()*Math.PI*2, spd=60+Math.random()*200;
            vx=Math.cos(a)*spd; vy=Math.sin(a)*spd;
            maxLife=life=0.35+Math.random()*0.55; sz=1.5+Math.random()*3.5;
        }
        boolean tick(double dt){x+=vx*dt;y+=vy*dt;vx*=0.9;vy*=0.9;vy+=120*dt;life-=dt;return life<=0;}
        void draw(Graphics2D g){
            float a=(float)Math.max(0,life/maxLife);
            int al=Math.max(0,Math.min(255,(int)(a*220)));
            g.setColor(new Color(c.getRed(),c.getGreen(),c.getBlue(),al));
            int s=Math.max(1,(int)(sz*a)); g.fillOval((int)(x-s/2.0),(int)(y-s/2.0),s,s);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SIMULATION PANEL
    // ══════════════════════════════════════════════════════════════════════════
    class SimPanel extends JPanel {
        Body[] bodies;
        List<Spark> sparks = new ArrayList<>();
        boolean running=false, gravOn=false, showTrails=true, showArrows=true, showEnergy=true;
        boolean wallL=false, wallR=false;
        double elasticity=0.8;
        double initKE=0;

        Point drag0=null, drag1=null;
        Body dragBody=null;

        // stars
        float[] stX,stY,stSz,stBr;

        javax.swing.Timer loop;

        SimPanel(){
            setBackground(BG0); setOpaque(true);
            Random rng=new Random(42); int ns=240;
            stX=new float[ns];stY=new float[ns];stSz=new float[ns];stBr=new float[ns];
            for(int i=0;i<ns;i++){stX[i]=rng.nextFloat();stY[i]=rng.nextFloat();stSz[i]=rng.nextFloat()*1.4f+0.3f;stBr[i]=rng.nextFloat()*0.55f+0.2f;}
            bodies=new Body[]{new Body(0,BOX_CLR[0]),new Body(1,BOX_CLR[1]),new Body(2,BOX_CLR[2])};
            bodies[2].active=false;
            place();
            addMouseListener(new MouseAdapter(){
                @Override public void mousePressed(MouseEvent e){
                    if(running) return;
                    dragBody=bodyAt(e.getX(), e.getY());
                    if(dragBody==null) return;
                    requestFocusInWindow();
                    drag0=e.getPoint();
                    drag1=e.getPoint();
                }
                @Override public void mouseReleased(MouseEvent e){
                    if(dragBody==null || drag0==null) return;
                    int dx=e.getX()-drag0.x, dy=e.getY()-drag0.y;
                    double len=Math.hypot(dx, dy);
                    if(len<6){
                        dragBody.customLaunch=false;
                    } else {
                        dragBody.launchVx=dx*0.1;
                        dragBody.launchVy=dy*0.1;
                        dragBody.customLaunch=true;
                    }
                    drag0=null; drag1=null; dragBody=null; repaint();
                }
            });
            addMouseMotionListener(new MouseMotionAdapter(){
                @Override public void mouseDragged(MouseEvent e){
                    if(dragBody==null) return;
                    drag1=e.getPoint();
                    repaint();
                }
            });
            loop=new javax.swing.Timer(15,e->{if(running)tick(0.015);anim();repaint();});
            loop.start();
        }

        int floorY(){ return getHeight()-68; }
        int leftX() { return 38; }
        int rightX(){ return getWidth()-38; }

        void place(){
            place(true);
        }

        void place(boolean clearLaunch){
            int pw=getWidth()>10?getWidth():W-CTRL_W;
            int[] xs={pw/4, pw/2, 3*pw/4};
            for(int i=0;i<3;i++){
                Body b=bodies[i];
                b.x=xs[i];
                b.y=floorY()-b.side()/2;
                b.vx=b.vy=0;
                b.trail.clear();
                b.sx=b.sy=1;
                if(clearLaunch) b.customLaunch=false;
            }
            running=false; sparks.clear();
        }

        void launch(){
            place(false); initKE=0;
            for(Body b:bodies){
                if(!b.active)continue;
                if(b.customLaunch){
                    b.vx=b.launchVx;
                    b.vy=b.launchVy;
                } else {
                    switch(b.dir){case 0:b.vx=b.initVel;b.vy=0;break;case 1:b.vx=-b.initVel;b.vy=0;break;case 2:b.vx=0;b.vy=-b.initVel;break;default:b.vx=0;b.vy=b.initVel;}
                }
                initKE+=0.5*b.mass*(b.vx*b.vx+b.vy*b.vy);
            }
            running=true;
        }

        Body bodyAt(int mx,int my){
            for(int i=bodies.length-1;i>=0;i--){
                Body b=bodies[i];
                if(!b.active) continue;
                double s=b.side();
                double lx=b.x-s/2, ty=b.y-s/2, rx=b.x+s/2, by=b.y+s/2;
                if(mx>=lx && mx<=rx && my>=ty && my<=by) return b;
            }
            return null;
        }

        void anim(){
            for(Body b:bodies){b.sx+=(1-b.sx)*0.16;b.sy+=(1-b.sy)*0.16;if(b.glow>0)b.glow-=0.035;}
        }

        void tick(double dt){
            int fy=floorY(), lx=leftX(), rx=rightX();
            for(Body b:bodies){
                if(!b.active)continue;
                if(gravOn) b.vy+=GRAVITY*dt;
                b.x+=b.vx*dt; b.y+=b.vy*dt;
                if(showTrails){b.trail.add(new double[]{b.x,b.y});if(b.trail.size()>90)b.trail.remove(0);}
                // floor
                double half=b.side()/2;
                if(b.y+half>fy){
                    b.y=fy-half;
                    if(Math.abs(b.vy)>3 || gravOn){
                        b.vy=-b.vy*elasticity;
                        b.vx*=(0.65+0.35*elasticity);
                        b.sx=1.45; b.sy=0.65; b.glow=1;
                        spark(b.x,b.y+half,b.col,4);
                    } else {
                        b.vy=0;
                    }
                }
                if(b.y-half<5){b.y=5+half;b.vy=Math.abs(b.vy)*elasticity;}
                if(wallL&&b.x-half<lx){b.x=lx+half;b.vx=Math.abs(b.vx)*elasticity;b.sx=0.65;b.sy=1.35;b.glow=1;spark(lx,b.y,b.col,5);}
                if(wallR&&b.x+half>rx){b.x=rx-half;b.vx=-Math.abs(b.vx)*elasticity;b.sx=0.65;b.sy=1.35;b.glow=1;spark(rx,b.y,b.col,5);}
            }
            // body-body collisions
            for(int i=0;i<bodies.length;i++) for(int j=i+1;j<bodies.length;j++) if(bodies[i].active&&bodies[j].active) collide(bodies[i],bodies[j]);
            sparks.removeIf(s->s.tick(dt));
        }

        static Color mid(Color a,Color b){return new Color((a.getRed()+b.getRed())/2,(a.getGreen()+b.getGreen())/2,(a.getBlue()+b.getBlue())/2);}

        void spark(double x,double y,Color c,int n){for(int i=0;i<n;i++)sparks.add(new Spark(x,y,c));}

        void collide(Body a,Body b){
            double hs=(a.side()+b.side())/2;
            double dx=b.x-a.x,dy=b.y-a.y;
            double ox=hs-Math.abs(dx),oy=hs-Math.abs(dy);
            if(ox<=0||oy<=0)return;
            double nx,ny,ov;
            if(ox<oy){nx=dx<0?-1:1;ny=0;ov=ox;}else{nx=0;ny=dy<0?-1:1;ov=oy;}
            double tm=a.mass+b.mass;
            a.x-=nx*ov*(b.mass/tm); a.y-=ny*ov*(b.mass/tm);
            b.x+=nx*ov*(a.mass/tm); b.y+=ny*ov*(a.mass/tm);
            double rvn=(b.vx-a.vx)*nx+(b.vy-a.vy)*ny;
            if(rvn>0)return;
            double j=-(1+elasticity)*rvn/(1.0/a.mass+1.0/b.mass);
            a.vx-=j/a.mass*nx;a.vy-=j/a.mass*ny;
            b.vx+=j/b.mass*nx;b.vy+=j/b.mass*ny;
            double fr=0.12*(1-elasticity*0.5);
            double tx=-ny,ty=nx,rvt=(b.vx-a.vx)*tx+(b.vy-a.vy)*ty;
            double jt=-fr*rvt/(1.0/a.mass+1.0/b.mass);
            a.vx-=jt/a.mass*tx;a.vy-=jt/a.mass*ty;b.vx+=jt/b.mass*tx;b.vy+=jt/b.mass*ty;
            if(nx==0){a.sx=1.35;a.sy=0.7;b.sx=1.35;b.sy=0.7;}else{a.sx=0.7;a.sy=1.35;b.sx=0.7;b.sy=1.35;}
            a.glow=1;b.glow=1;
            int cx=(int)((a.x+b.x)/2),cy=(int)((a.y+b.y)/2);
            for(int i=0;i<14;i++)sparks.add(new Spark(cx,cy,mid(a.col,b.col)));
            for(int i=0;i<5;i++){sparks.add(new Spark(cx,cy,a.col));sparks.add(new Spark(cx,cy,b.col));}
        }

        // ── PAINT ─────────────────────────────────────────────────────────────
        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2=(Graphics2D)g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
            int pw=getWidth(),ph=getHeight();
            drawBg(g2,pw,ph);
            drawGrid(g2,pw,ph);
            drawStars(g2,pw,ph);
            drawArena(g2,pw,ph);
            if(showTrails)drawTrails(g2);
            if(showArrows)drawArrows(g2);
            drawDragPreview(g2);
            drawSparks(g2);
            drawBodies(g2);
            drawHUD(g2,pw,ph);
        }

        void drawBg(Graphics2D g2,int pw,int ph){
            GradientPaint bg=new GradientPaint(pw/2f,0,BG1,pw/2f,ph,BG0);
            g2.setPaint(bg);g2.fillRect(0,0,pw,ph);
        }

        void drawGrid(Graphics2D g2,int pw,int ph){
            g2.setStroke(new BasicStroke(0.4f));g2.setColor(GRID);
            for(int x=0;x<pw;x+=48)g2.drawLine(x,0,x,ph);
            for(int y=0;y<ph;y+=48)g2.drawLine(0,y,pw,y);
            g2.setStroke(new BasicStroke(1));
        }

        void drawStars(Graphics2D g2,int pw,int ph){
            long t=System.currentTimeMillis();
            for(int i=0;i<stX.length;i++){
                float br=(float)(stBr[i]*(0.5+0.5*Math.sin(t*0.0003*stBr[i]*3+i)));
                g2.setColor(new Color(1f,1f,1f,Math.max(0.04f,Math.min(0.75f,br))));
                int r=Math.max(1,(int)(stSz[i]*1.3));
                g2.fillOval((int)(stX[i]*pw),(int)(stY[i]*ph),r,r);
            }
        }

        void drawArena(Graphics2D g2,int pw,int ph){
            int fy=floorY(),lx=leftX(),rx=rightX();
            // floor glow
            for(int i=4;i>=1;i--){
                float a=0.05f*i;
                g2.setColor(new Color(70,120,255,(int)(a*255)));
                g2.setStroke(new BasicStroke(i*3f));
                g2.drawLine(lx,fy+i*2,rx,fy+i*2);
            }
            g2.setStroke(new BasicStroke(2f));
            GradientPaint floorLine=new GradientPaint(lx,fy,new Color(50,100,255,0),pw/2f,fy,new Color(80,150,255,220));
            g2.setPaint(floorLine);g2.drawLine(lx,fy,pw/2,fy);
            GradientPaint floorLine2=new GradientPaint(pw/2f,fy,new Color(80,150,255,220),rx,fy,new Color(50,100,255,0));
            g2.setPaint(floorLine2);g2.drawLine(pw/2,fy,rx,fy);
            g2.setStroke(new BasicStroke(1));

            // floor ticks
            g2.setColor(new Color(60,100,200,55));
            for(int x=lx;x<rx;x+=24)g2.drawLine(x,fy,x,fy+5);

            // walls
            if(wallL)drawWall(g2,lx,fy,true);
            if(wallR)drawWall(g2,rx,fy,false);
        }

        void drawWall(Graphics2D g2,int wx,int fy,boolean left){
            // glow beam
            GradientPaint wg=new GradientPaint(wx,0,new Color(80,140,255,left?0:55),wx,fy,new Color(80,140,255,left?55:0));
            g2.setPaint(wg);g2.setStroke(new BasicStroke(2.5f));g2.drawLine(wx,0,wx,fy);
            // chevrons
            g2.setColor(new Color(70,130,255,60));g2.setStroke(new BasicStroke(1.2f));
            int step=26,d=left?7:-7;
            for(int y=14;y<fy;y+=step){g2.drawLine(wx,y,wx+d,y+10);g2.drawLine(wx+d,y+10,wx,y+20);}
            g2.setStroke(new BasicStroke(1));
            g2.setFont(new Font("Consolas",Font.BOLD,9));
            g2.setColor(new Color(90,155,255,160));
            g2.drawString("WALL",wx+(left?3:-22),16);
        }

        void drawTrails(Graphics2D g2){
            for(Body b:bodies){
                if(!b.active||b.trail.size()<2)continue;
                for(int i=1;i<b.trail.size();i++){
                    float a=(float)i/b.trail.size()*0.32f;
                    g2.setColor(new Color(b.col.getRed(),b.col.getGreen(),b.col.getBlue(),(int)(a*255)));
                    g2.setStroke(new BasicStroke(Math.max(0.5f,a*3.5f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                    double[]p0=b.trail.get(i-1),p1=b.trail.get(i);
                    g2.drawLine((int)p0[0],(int)p0[1],(int)p1[0],(int)p1[1]);
                }
                g2.setStroke(new BasicStroke(1));
            }
        }

        void drawArrows(Graphics2D g2){
            for(Body b:bodies){
                if(!b.active)continue;
                double spd=running?Math.hypot(b.vx,b.vy):(b.customLaunch?Math.hypot(b.launchVx,b.launchVy):b.initVel);
                if(spd<1)continue;
                double ax,ay;
                if(running&&(Math.abs(b.vx)>1||Math.abs(b.vy)>1)){
                    ax=b.vx/spd;ay=b.vy/spd;
                } else if(b.customLaunch){
                    ax=b.launchVx/spd;ay=b.launchVy/spd;
                } else {
                    switch(b.dir){case 0:ax=1;ay=0;break;case 1:ax=-1;ay=0;break;case 2:ax=0;ay=-1;break;default:ax=0;ay=1;}
                }
                double len=Math.min(18+spd*0.5,240);
                double ex=b.x+ax*len,ey=b.y+ay*len;
                // check overlap with other arrows for color blend
                Color ac=b.col;
                for(Body ob:bodies){
                    if(ob==b||!ob.active)continue;
                    double os=running?Math.hypot(ob.vx,ob.vy):ob.initVel;
                    if(os<1)continue;
                    if(Math.hypot(ob.x-b.x,ob.y-b.y)<len+Math.min(18+os*0.5,240)) ac=mid(ac,ob.col);
                }
                int cr=ac.getRed(),cg=ac.getGreen(),cb=ac.getBlue();
                // glow
                g2.setStroke(new BasicStroke(9f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(cr,cg,cb,22));g2.drawLine((int)b.x,(int)b.y,(int)ex,(int)ey);
                g2.setStroke(new BasicStroke(4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(cr,cg,cb,55));g2.drawLine((int)b.x,(int)b.y,(int)ex,(int)ey);
                // main
                g2.setStroke(new BasicStroke(2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(cr,cg,cb,200));g2.drawLine((int)b.x,(int)b.y,(int)ex,(int)ey);
                // head
                double ang=Math.atan2(ay,ax),hl=11;
                int[]hx={(int)ex,(int)(ex-hl*Math.cos(ang-0.42)),(int)(ex-hl*Math.cos(ang+0.42))};
                int[]hy={(int)ey,(int)(ey-hl*Math.sin(ang-0.42)),(int)(ey-hl*Math.sin(ang+0.42))};
                g2.setColor(new Color(cr,cg,cb,220));g2.fillPolygon(hx,hy,3);
                // speed tag
                g2.setFont(new Font("Consolas",Font.BOLD,10));
                g2.setColor(new Color(cr,cg,cb,185));
                g2.drawString(String.format("%.0f",spd),(int)(ex+ax*7),(int)(ey+ay*7));
            }
            g2.setStroke(new BasicStroke(1));
        }

        void drawDragPreview(Graphics2D g2){
            if(dragBody==null || drag0==null || drag1==null) return;
            int dx=drag1.x-drag0.x, dy=drag1.y-drag0.y;
            if(Math.hypot(dx,dy)<5) return;
            g2.setColor(new Color(255,255,255,85));
            g2.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,1f,new float[]{5f,5f},0f));
            g2.drawLine(drag0.x,drag0.y,drag1.x,drag1.y);
            g2.setStroke(new BasicStroke(1));
            double ang=Math.atan2(dy,dx);
            int[]hx={drag1.x,(int)(drag1.x-12*Math.cos(ang-.4)),(int)(drag1.x-12*Math.cos(ang+.4))};
            int[]hy={drag1.y,(int)(drag1.y-12*Math.sin(ang-.4)),(int)(drag1.y-12*Math.sin(ang+.4))};
            g2.setColor(new Color(255,255,255,145));
            g2.fillPolygon(hx,hy,3);
            g2.fillOval(drag0.x-4,drag0.y-4,8,8);
        }

        void drawSparks(Graphics2D g2){for(Spark s:sparks)s.draw(g2);}

        void drawBodies(Graphics2D g2){
            for(Body b:bodies){
                if(!b.active)continue;
                double s=b.side(),bx=b.x-s/2*b.sx,by=b.y-s/2*b.sy,bw=s*b.sx,bh=s*b.sy;
                int x=(int)bx,y=(int)by,w=(int)bw,h=(int)bh;
                // glow pulse
                if(b.glow>0){
                    int ga=(int)(b.glow*70);
                    RadialGradientPaint gl=new RadialGradientPaint((float)b.x,(float)b.y,(float)(s*1.6f),
                        new float[]{0f,0.5f,1f},new Color[]{new Color(b.col.getRed(),b.col.getGreen(),b.col.getBlue(),ga),new Color(b.col.getRed(),b.col.getGreen(),b.col.getBlue(),ga/3),new Color(0,0,0,0)});
                    g2.setPaint(gl);g2.fillOval((int)(b.x-s*1.6),(int)(b.y-s*1.6),(int)(s*3.2),(int)(s*3.2));
                }
                // shadow
                g2.setColor(new Color(0,0,0,50));g2.fillRoundRect(x+4,y+5,w,h,10,10);
                // fill
                GradientPaint fp=new GradientPaint(x,y,brighter(b.col,60),x,y+h,darker(b.col));
                g2.setPaint(fp);g2.fillRoundRect(x,y,w,h,10,10);
                // inner highlight top
                GradientPaint hp=new GradientPaint(x,y,new Color(255,255,255,55),x,y+h/3,new Color(255,255,255,0));
                g2.setPaint(hp);g2.fillRoundRect(x,y,w,h/2,10,10);
                // edge
                g2.setColor(new Color(b.col.getRed(),b.col.getGreen(),b.col.getBlue(),165));
                g2.setStroke(new BasicStroke(1.6f));g2.drawRoundRect(x,y,w,h,10,10);g2.setStroke(new BasicStroke(1));
                // specular glint top-left
                g2.setColor(new Color(255,255,255,48));g2.setStroke(new BasicStroke(1f));
                g2.drawLine(x+6,y+2,x+w-6,y+2);g2.setStroke(new BasicStroke(1));
                // label
                g2.setFont(new Font("Consolas",Font.BOLD,12));
                g2.setColor(new Color(255,255,255,210));
                FontMetrics fm=g2.getFontMetrics();
                g2.drawString(BOX_NM[b.id],(int)(b.x-fm.stringWidth(BOX_NM[b.id])/2.0),(int)(b.y+4));
                // mass tag
                g2.setFont(new Font("Consolas",Font.PLAIN,9));
                g2.setColor(new Color(255,255,255,130));
                String ml=String.format("%.0fkg",b.mass);fm=g2.getFontMetrics();
                g2.drawString(ml,(int)(b.x-fm.stringWidth(ml)/2.0),(int)(b.y+15));
                // velocity readout if running
                if(running&&(Math.abs(b.vx)>1||Math.abs(b.vy)>1)){
                    g2.setFont(new Font("Consolas",Font.PLAIN,9));
                    String vt=String.format("%.0fpx/s",Math.hypot(b.vx,b.vy));fm=g2.getFontMetrics();
                    g2.setColor(new Color(b.col.getRed(),b.col.getGreen(),b.col.getBlue(),185));
                    g2.drawString(vt,(int)(b.x-fm.stringWidth(vt)/2.0),(int)(b.y+s/2*b.sy+14));
                }
            }
        }

        void drawHUD(Graphics2D g2,int pw,int ph){
            // energy bar top-left
            if(showEnergy){
                double tke=0; for(Body b:bodies)if(b.active)tke+=0.5*b.mass*(b.vx*b.vx+b.vy*b.vy);
                double ratio=initKE>0?Math.min(1,tke/initKE):0;
                int hx=10,hy=10,hw=190,hh=52;
                g2.setColor(new Color(6,10,24,188));g2.fillRoundRect(hx,hy,hw,hh,12,12);
                g2.setColor(new Color(45,70,160,90));g2.drawRoundRect(hx,hy,hw,hh,12,12);
                g2.setFont(new Font("Consolas",Font.BOLD,10));g2.setColor(new Color(160,195,240,200));
                g2.drawString("KINETIC ENERGY",hx+10,hy+15);
                int bx=hx+10,by=hy+22,bw=hw-20,bh=9;
                g2.setColor(new Color(12,20,48));g2.fillRoundRect(bx,by,bw,bh,6,6);
                Color ec=ratio>0.75?new Color(70,215,110):ratio>0.4?new Color(255,195,50):new Color(220,65,55);
                GradientPaint ep=new GradientPaint(bx,by,ec.brighter(),bx+bw,by,ec);
                g2.setPaint(ep);g2.fillRoundRect(bx,by,(int)(bw*ratio),bh,6,6);
                g2.setColor(new Color(80,120,200,80));g2.drawRoundRect(bx,by,bw,bh,6,6);
                g2.setFont(new Font("Consolas",Font.PLAIN,9));g2.setColor(new Color(155,185,230,185));
                g2.drawString(String.format("KE=%.0f  (%.0f%%)",tke,ratio*100),bx,hy+46);
            }
            // status bar bottom-centre
            String st=running?"● RUNNING":"◌ READY";
            Color sc=running?new Color(70,215,100):new Color(150,170,220);
            String info=String.format("  Elasticity %.0f%%  |  %s  |  Gravity %s",elasticity*100,st,gravOn?"ON":"OFF");
            g2.setFont(new Font("Consolas",Font.BOLD,11));
            FontMetrics fm=g2.getFontMetrics();
            int bw2=fm.stringWidth(info)+28,bh2=22;
            int bx2=pw/2-bw2/2,by2=ph-36;
            g2.setColor(new Color(5,9,22,180));g2.fillRoundRect(bx2,by2,bw2,bh2,10,10);
            g2.setColor(new Color(40,65,150,75));g2.drawRoundRect(bx2,by2,bw2,bh2,10,10);
            g2.setColor(sc);g2.drawString(st,bx2+12,by2+bh2/2+4);
            g2.setColor(new Color(165,185,230,200));g2.drawString(info.substring(st.length()),bx2+12+fm.stringWidth(st),by2+bh2/2+4);
        }
    }

    // ── colour helpers ────────────────────────────────────────────────────────
    static Color brighter(Color c,int amt){return new Color(Math.min(255,c.getRed()+amt),Math.min(255,c.getGreen()+amt),Math.min(255,c.getBlue()+amt));}
    static Color darker(Color c){return new Color(c.getRed()/2,c.getGreen()/2,c.getBlue()/2);}
    static Color mid(Color a,Color b){return new Color((a.getRed()+b.getRed())/2,(a.getGreen()+b.getGreen())/2,(a.getBlue()+b.getBlue())/2);}

    // ══════════════════════════════════════════════════════════════════════════
    // CONTROL PANEL
    // ══════════════════════════════════════════════════════════════════════════
    JPanel buildCtrl(){
        JPanel shell=new JPanel(new BorderLayout());
        shell.setBackground(PANEL);
        shell.setPreferredSize(new Dimension(CTRL_W,H));
        shell.setBorder(BorderFactory.createMatteBorder(0,1,0,0,new Color(35,55,130)));

        JPanel p=new JPanel(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setPaint(new GradientPaint(0,0,new Color(7,10,24),0,getHeight(),new Color(4,7,18)));
                g2.fillRect(0,0,getWidth(),getHeight());
                g2.setColor(new Color(45,80,170,12));g2.fillOval(-40,-30,getWidth()+80,140);
                g2.dispose();
            }
        };
        p.setOpaque(false);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setBorder(BorderFactory.createEmptyBorder(14,10,14,10));

        // header
        JLabel h1=mkLbl("COLLISION LAB",new Font("Consolas",Font.BOLD,17),new Color(210,228,255));h1.setAlignmentX(CENTER_ALIGNMENT);
        JLabel h2=mkLbl("Physics Engine v2",new Font("Consolas",Font.PLAIN,11),TXT1);h2.setAlignmentX(CENTER_ALIGNMENT);
        p.add(h1);p.add(vg(2));p.add(h2);p.add(vg(12));

        // per-box cards
        for(int i=0;i<3;i++) p.add(buildBoxCard(i));

        // global settings
        p.add(vg(8));
        JPanel gc=card("PHYSICS SETTINGS");

        gc.add(secLbl("Elasticity"));
        JPanel elRow=new JPanel(new BorderLayout(5,0));elRow.setBackground(new Color(0,0,0,0));elRow.setMaximumSize(new Dimension(999,34));
        JLabel elL=mkLbl("PLASTIC",new Font("Consolas",Font.PLAIN,8),new Color(90,110,165));
        JLabel elR=mkLbl("ELASTIC",new Font("Consolas",Font.PLAIN,8),new Color(90,110,165));
        JSlider elSl=mkSlider(0,100,80,null);
        JLabel elV=vLbl("80%");
        elSl.addChangeListener(e->{sim.elasticity=elSl.getValue()/100.0;elV.setText(elSl.getValue()+"%");});
        elRow.add(elL,BorderLayout.WEST);elRow.add(elSl,BorderLayout.CENTER);elRow.add(elR,BorderLayout.EAST);
        gc.add(elRow);gc.add(elV);gc.add(vg(5));

        gc.add(secLbl("Gravity"));
        JToggleButton gravB=mkToggle("Gravity OFF",false,new Color(22,38,80));
        gravB.addActionListener(e->{sim.gravOn=gravB.isSelected();gravB.setText(sim.gravOn?"Gravity ON":"Gravity OFF");});
        gc.add(gravB);gc.add(vg(5));

        gc.add(secLbl("Walls"));
        JPanel wr=new JPanel();wr.setBackground(new Color(0,0,0,0));wr.setLayout(new BoxLayout(wr,BoxLayout.X_AXIS));wr.setMaximumSize(new Dimension(999,32));
        JToggleButton wLB=mkToggle("◀ Left",false,new Color(20,36,76));
        JToggleButton wRB=mkToggle("Right ▶",false,new Color(20,36,76));
        JToggleButton wBB=mkToggle("Both",false,new Color(20,36,76));
        wLB.addActionListener(e->{sim.wallL=wLB.isSelected();if(wLB.isSelected()){wRB.setSelected(false);wBB.setSelected(false);sim.wallR=false;}});
        wRB.addActionListener(e->{sim.wallR=wRB.isSelected();if(wRB.isSelected()){wLB.setSelected(false);wBB.setSelected(false);sim.wallL=false;}});
        wBB.addActionListener(e->{boolean b=wBB.isSelected();sim.wallL=sim.wallR=b;wLB.setSelected(false);wRB.setSelected(false);});
        wr.add(wLB);wr.add(javax.swing.Box.createHorizontalStrut(4));wr.add(wRB);wr.add(javax.swing.Box.createHorizontalStrut(4));wr.add(wBB);
        gc.add(wr);gc.add(vg(5));

        gc.add(secLbl("Display"));
        JPanel dr=new JPanel();dr.setBackground(new Color(0,0,0,0));dr.setLayout(new BoxLayout(dr,BoxLayout.X_AXIS));dr.setMaximumSize(new Dimension(999,32));
        JToggleButton tArr=mkToggle("Arrows",true,new Color(18,34,72));
        JToggleButton tTrl=mkToggle("Trails",true,new Color(18,34,72));
        JToggleButton tEn=mkToggle("Energy",true,new Color(18,34,72));
        tArr.addActionListener(e->sim.showArrows=tArr.isSelected());
        tTrl.addActionListener(e->sim.showTrails=tTrl.isSelected());
        tEn.addActionListener(e->sim.showEnergy=tEn.isSelected());
        dr.add(tArr);dr.add(javax.swing.Box.createHorizontalStrut(4));dr.add(tTrl);dr.add(javax.swing.Box.createHorizontalStrut(4));dr.add(tEn);
        gc.add(dr);
        p.add(gc);p.add(vg(10));

        // action buttons
        p.add(mkBtn("▶  LAUNCH",new Color(25,75,40),new Color(70,210,100),e->sim.launch()));
        p.add(vg(5));
        p.add(mkBtn("↺  RESET", new Color(18,38,80),new Color(90,150,240),e->sim.place()));
        if(onBack!=null){p.add(vg(8));p.add(mkBtn("← HOME",new Color(10,20,50),new Color(85,125,215),e->{setVisible(false);dispose();if(onBack!=null)SwingUtilities.invokeLater(onBack);}));}

        JScrollPane sc=new JScrollPane(p);
        sc.setBorder(BorderFactory.createEmptyBorder());
        sc.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sc.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        sc.getVerticalScrollBar().setUnitIncrement(14);
        sc.getViewport().setOpaque(false);sc.setOpaque(false);
        shell.add(sc,BorderLayout.CENTER);
        return shell;
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
        JLabel title = new JLabel("  AI ASSISTANT  —  Ask anything about collision physics");
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
        chatDisplay.setText("Assistant: Hello! I'm here to help you understand this Collision Physics Engine.\n"
            + "Ask me about: momentum conservation, elasticity, gravity effects, wall collisions, launch vectors,\n"
            + "kinetic energy tracking, and how each control changes the simulation.\n");

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
    static final String NVIDIA_API_KEY = SolarSystemEngine.NVIDIA_API_KEY;

    static final String SYSTEM_PROMPT =
        "You are an expert assistant embedded inside a Java Swing Collision Physics Engine simulation. " +
        "Answer questions about momentum conservation, kinetic energy, elasticity, gravity toggles, launch vectors, and wall collisions. " +
        "Explain what UI controls do in concise technical language. Keep answers under 3 sentences. " +
        "If a question is unrelated to physics or this simulation, gently redirect.";

    String callNvidiaAPI(String userMessage) {
        try {
            String apiKey = System.getenv("NVIDIA_API_KEY");
            if ((apiKey == null || apiKey.isBlank()) && !NVIDIA_API_KEY.isBlank()) {
                apiKey = NVIDIA_API_KEY;
            }
            if (apiKey == null || apiKey.isBlank()) {
                return "API key not set. Set NVIDIA_API_KEY environment variable or paste key in NVIDIA_API_KEY constant.";
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
                if (status == 401 || status == 403) return "API key invalid or missing. Set NVIDIA_API_KEY environment variable.";
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

    JPanel buildBoxCard(int idx){
        Color bc=BOX_CLR[idx]; Body body=sim.bodies[idx];
        JPanel card=new JPanel(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                RoundRectangle2D r=new RoundRectangle2D.Double(.5,.5,getWidth()-1,getHeight()-1,14,14);
                g2.setPaint(new GradientPaint(0,0,CARD0,0,getHeight(),CARD1));g2.fill(r);
                g2.setColor(new Color(bc.getRed(),bc.getGreen(),bc.getBlue(),130));g2.fillRoundRect(0,0,3,getHeight(),3,3);
                g2.setColor(CEDGE);g2.draw(r);g2.dispose();
            }
        };
        card.setOpaque(false);card.setLayout(new BoxLayout(card,BoxLayout.Y_AXIS));
        card.setAlignmentX(CENTER_ALIGNMENT);card.setMaximumSize(new Dimension(999,2000));
        card.setBorder(BorderFactory.createEmptyBorder(7,10,9,10));

        JPanel hdr=new JPanel(new BorderLayout(5,0));hdr.setBackground(new Color(0,0,0,0));hdr.setMaximumSize(new Dimension(999,26));
        JLabel tit=mkLbl(BOX_NM[idx]+" BOX",new Font("Consolas",Font.BOLD,12),bc);
        JToggleButton actB=mkToggle("ON",body.active,darker(bc));
        actB.setFont(new Font("Consolas",Font.BOLD,9));
        actB.addActionListener(e->{body.active=actB.isSelected();actB.setText(body.active?"ON":"OFF");sim.place();});
        hdr.add(tit,BorderLayout.WEST);hdr.add(actB,BorderLayout.EAST);
        card.add(hdr);card.add(vg(6));

        // mass
        card.add(cLbl("Mass (kg)",bc));
        JSlider ms=mkSlider(1,200,(int)body.mass,bc);
        JLabel mv=vLbl(body.mass+" kg");
        ms.addChangeListener(e->{body.mass=ms.getValue();mv.setText(ms.getValue()+" kg");sim.place();});
        card.add(ms);card.add(mv);card.add(vg(4));

        // velocity
        card.add(cLbl("Init Velocity",bc));
        JSlider vs=mkSlider(0,600,(int)body.initVel,bc);
        JLabel vv=vLbl((int)body.initVel+" px/s");
        vs.addChangeListener(e->{body.initVel=vs.getValue();vv.setText(vs.getValue()+" px/s");});
        card.add(vs);card.add(vv);card.add(vg(4));

        // direction buttons
        card.add(cLbl("Direction",bc));
        JPanel dp=new JPanel();dp.setBackground(new Color(0,0,0,0));dp.setLayout(new BoxLayout(dp,BoxLayout.X_AXIS));dp.setMaximumSize(new Dimension(999,30));
        String[]dn={"→","←","↑","↓"};
        ButtonGroup bg=new ButtonGroup();
        for(int d=0;d<4;d++){
            final int dir=d; Color fbc=bc;
            JToggleButton db=new JToggleButton(dn[d]){
                @Override protected void paintComponent(Graphics g){
                    Graphics2D g2=(Graphics2D)g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    Color base=isSelected()?new Color(fbc.getRed(),fbc.getGreen(),fbc.getBlue(),150):new Color(16,26,52);
                    g2.setPaint(new GradientPaint(0,0,base.brighter(),0,getHeight(),base));
                    g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,7,7);
                    if(isSelected()){g2.setColor(new Color(fbc.getRed(),fbc.getGreen(),fbc.getBlue(),170));g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,7,7);}
                    g2.dispose();super.paintComponent(g);
                }
            };
            db.setFont(new Font("Consolas",Font.BOLD,13));db.setForeground(body.dir==d?bc:new Color(130,150,200));
            db.setContentAreaFilled(false);db.setOpaque(false);db.setBorder(BorderFactory.createEmptyBorder(2,6,2,6));db.setFocusPainted(false);
            db.setSelected(body.dir==d);
            db.addActionListener(e->{body.dir=dir;db.setForeground(fbc);});
            db.addItemListener(e->db.setForeground(db.isSelected()?fbc:new Color(130,150,200)));
            bg.add(db);dp.add(db);if(d<3)dp.add(javax.swing.Box.createHorizontalStrut(3));
        }
        card.add(dp);card.add(vg(4));

        // boost
        card.add(cLbl("Launch Boost",bc));
        JSlider bs=mkSlider(0,300,0,bc);
        JLabel bv=vLbl("+0 px/s");
        bs.addChangeListener(e->{double bonus=bs.getValue()*0.35;body.initVel=vs.getValue()+bonus;bv.setText(String.format("+%.0f px/s",bonus));});
        card.add(bs);card.add(bv);

        p_list.add(card);return card;
    }
    List<JPanel> p_list=new ArrayList<>();

    JSlider mkSlider(int mn,int mx,int v,Color tint){
        JSlider s=new JSlider(mn,mx,Math.max(mn,Math.min(mx,v)));
        s.setBackground(new Color(0,0,0,0));s.setOpaque(false);
        s.setForeground(tint!=null?tint:new Color(80,140,240));s.setMaximumSize(new Dimension(999,26));return s;
    }
    JLabel mkLbl(String t,Font f,Color c){JLabel l=new JLabel(t);l.setFont(f);l.setForeground(c);return l;}
    JLabel secLbl(String t){JLabel l=new JLabel(t);l.setFont(new Font("Consolas",Font.BOLD,10));l.setForeground(TXT1);l.setAlignmentX(LEFT_ALIGNMENT);l.setBorder(BorderFactory.createEmptyBorder(2,0,1,0));return l;}
    JLabel cLbl(String t,Color c){JLabel l=new JLabel(t);l.setFont(new Font("Consolas",Font.BOLD,10));l.setForeground(new Color(c.getRed(),c.getGreen(),c.getBlue(),185));l.setAlignmentX(LEFT_ALIGNMENT);l.setBorder(BorderFactory.createEmptyBorder(2,0,1,0));return l;}
    JLabel vLbl(String t){JLabel l=new JLabel(t);l.setFont(new Font("Consolas",Font.BOLD,10));l.setForeground(GOLD);l.setAlignmentX(LEFT_ALIGNMENT);return l;}
    Component vg(int h){return javax.swing.Box.createVerticalStrut(h);}

    JPanel card(String title){
        JPanel c=new JPanel(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                RoundRectangle2D r=new RoundRectangle2D.Double(.5,.5,getWidth()-1,getHeight()-1,14,14);
                g2.setPaint(new GradientPaint(0,0,CARD0,0,getHeight(),CARD1));g2.fill(r);g2.setColor(CEDGE);g2.draw(r);g2.dispose();
            }
        };
        c.setOpaque(false);c.setLayout(new BoxLayout(c,BoxLayout.Y_AXIS));c.setAlignmentX(CENTER_ALIGNMENT);c.setMaximumSize(new Dimension(999,2000));c.setBorder(BorderFactory.createEmptyBorder(7,10,9,10));
        JLabel tl=new JLabel(title,SwingConstants.CENTER);tl.setFont(new Font("Consolas",Font.BOLD,11));tl.setForeground(new Color(130,175,255));tl.setAlignmentX(CENTER_ALIGNMENT);tl.setBorder(BorderFactory.createEmptyBorder(0,0,5,0));
        c.add(tl);return c;
    }

    JToggleButton mkToggle(String text,boolean sel,Color bg){
        JToggleButton b=new JToggleButton(text,sel){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                Color base=isSelected()?new Color(bg.getRed()+25,bg.getGreen()+25,bg.getBlue()+35,215):new Color(bg.getRed(),bg.getGreen(),bg.getBlue(),155);
                g2.setPaint(new GradientPaint(0,0,base.brighter(),0,getHeight(),base));
                g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,9,9);
                if(isSelected()){g2.setColor(new Color(80,135,235,95));g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,9,9);}
                g2.dispose();super.paintComponent(g);
            }
        };
        b.setFont(new Font("Consolas",Font.BOLD,10));b.setForeground(sel?TXT0:TXT1);
        b.setContentAreaFilled(false);b.setOpaque(false);b.setBorder(BorderFactory.createEmptyBorder(4,7,4,7));
        b.setFocusPainted(false);b.setMaximumSize(new Dimension(999,30));b.setAlignmentX(CENTER_ALIGNMENT);
        b.addItemListener(e->b.setForeground(b.isSelected()?TXT0:TXT1));return b;
    }

    JButton mkBtn(String text,Color bg,Color fg,ActionListener al){
        JButton b=new JButton(text){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                Color base=getModel().isPressed()?bg.darker():getModel().isRollover()?bg.brighter():bg;
                g2.setPaint(new GradientPaint(0,0,base.brighter(),0,getHeight(),base));
                g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,11,11);
                g2.setColor(new Color(fg.getRed(),fg.getGreen(),fg.getBlue(),75));g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,11,11);
                g2.dispose();super.paintComponent(g);
            }
        };
        b.setForeground(fg);b.setFont(new Font("Consolas",Font.BOLD,12));b.setContentAreaFilled(false);b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(8,12,8,12));b.setFocusPainted(false);b.setMaximumSize(new Dimension(999,40));
        b.setAlignmentX(CENTER_ALIGNMENT);b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));b.addActionListener(al);return b;
    }
}