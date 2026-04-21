import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class Body {
    BodyType type;
    double x,y,vx,vy,mass,radius;
    double orbitDist,orbitAngle,orbitEcc,orbitSpeed;
    boolean usesKeplerOrbit;
    double shuttleAngle=0;
    boolean thrustMain=false,thrustLeft=false,thrustRight=false,thrustReverse=false;
    int colorIdx;
    boolean hasRings; double ringTilt;
    int moons; double[] moonAngle,moonSpeed,moonDist;
    List<Point2D.Double> trail = new ArrayList<>();
    boolean absorbed=false; String name="";
    double bhRotation=0;
    double pulsarPhase=Math.random()*Math.PI*2, pulsarPeriod=0.8+Math.random()*1.5;
    Body wormholePartner=null; double whPulse=0;
    boolean spaghettifying=false;
    double spaghettiProgress=0, spaghettiRaw=0, spaghettiAngle=0;
    Body spaghettiTarget=null;

    Body(double x,double y,double mass,double radius,BodyType type,int colorIdx){
        this.x=x;this.y=y;this.mass=mass;this.radius=radius;this.type=type;this.colorIdx=colorIdx;
        hasRings=(type==BodyType.PLANET)&&radius>12&&Math.random()<0.35; ringTilt=0.2+Math.random()*0.35;
        moons=(type==BodyType.PLANET&&radius>9)?(int)(Math.random()*3):0;
        moonAngle=new double[moons]; moonSpeed=new double[moons]; moonDist=new double[moons];
        for(int i=0;i<moons;i++){
            moonAngle[i]=Math.random()*Math.PI*2;
            moonSpeed[i]=(Math.random()*0.04+0.02)*(Math.random()<0.5?1:-1);
            moonDist[i]=radius*1.9+Math.random()*radius;
        }
    }
    void configureMoons(int moonCount){
        moons=Math.max(0,moonCount);
        moonAngle=new double[moons];
        moonSpeed=new double[moons];
        moonDist=new double[moons];
        for(int i=0;i<moons;i++){
            moonAngle[i]=Math.random()*Math.PI*2;
            moonSpeed[i]=(Math.random()*0.04+0.02)*(Math.random()<0.5?1:-1);
            moonDist[i]=radius*1.9+Math.random()*radius;
        }
    }
    void setupKeplerOrbit(double cx,double cy,double sm){
        double dx=x-cx,dy=y-cy; orbitDist=Math.sqrt(dx*dx+dy*dy); orbitAngle=Math.atan2(dy,dx);
        double e=Math.min(orbitEcc,0.85); orbitSpeed=Math.sqrt(SolarSystemEngine.G*sm/(orbitDist*(1+e)))*0.012;
        usesKeplerOrbit=true;
    }
    Point2D.Double keplerPos(double cx,double cy){
        double e=Math.min(orbitEcc,0.85);
        double r=orbitDist*(1-e*e)/(1+e*Math.cos(orbitAngle));
        return new Point2D.Double(cx+r*Math.cos(orbitAngle),cy+r*Math.sin(orbitAngle));
    }
}
