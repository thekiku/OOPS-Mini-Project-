import java.awt.Color;
import java.awt.Graphics2D;

public class Particle {
    double x,y,vx,vy; float size; Color color; int life,maxLife;
    Particle(double x,double y,Color c){this(x,y,c,(Math.random()-0.5)*6,(Math.random()-0.5)*6,1.5f);}
    Particle(double x,double y,Color c,double vx,double vy,float size){
        this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.color=c;this.size=size;
        maxLife=28+(int)(Math.random()*38);life=maxLife;
    }
    void update(double dt){x+=vx*dt;y+=vy*dt;vx*=0.93;vy*=0.93;life--;}
    boolean dead(){return life<=0;}
    void draw(Graphics2D g2){
        float alpha=(float)life/maxLife;
        g2.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),(int)(alpha*195)));
        int s=Math.max((int)(size*alpha*3),1);
        g2.fillOval((int)(x-s/2.0),(int)(y-s/2.0),s,s);
    }
}
