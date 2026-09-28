package com.lastcircle.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Random;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Original offline 3D tropical-island battle royale. Geometry is generated; no game assets are copied. */
public final class GameView extends FrameLayout {
    private final World world = new World();
    private final Controls overlay;
    private final GLSurfaceView gl;
    GameView(Context context) {
        super(context);
        gl = new GLSurfaceView(context);
        gl.setEGLContextClientVersion(2);
        gl.setRenderer(new Scene());
        gl.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        addView(gl, new LayoutParams(-1,-1));
        overlay = new Controls(context);
        addView(overlay, new LayoutParams(-1,-1));
        setKeepScreenOn(true);
    }
    @Override protected void onDetachedFromWindow(){gl.onPause();super.onDetachedFromWindow();}

    private static final class Bot {
        float x,z,hp=70,cd,theta;boolean alive=true;
        Bot(float x,float z){this.x=x;this.z=z;}
    }
    private static final class Bullet {
        float x,z,dx,dz,life;boolean friendly;
        Bullet(float x,float z,float dx,float dz,boolean friendly){this.x=x;this.z=z;this.dx=dx;this.dz=dz;this.friendly=friendly;life=1.3f;}
    }
    private static final class Building {
        float x,z,w,d,h;int type;
        Building(float x,float z,float w,float d,float h,int type){this.x=x;this.z=z;this.w=w;this.d=d;this.h=h;this.type=type;}
    }
    private static final class Pickup {
        float x,z;int type;boolean taken;
        Pickup(float x,float z,int type){this.x=x;this.z=z;this.type=type;}
    }
    private static final class World {
        final Random random=new Random(2410);
        final ArrayList<Bot> bots=new ArrayList<>();
        final ArrayList<Bullet> bullets=new ArrayList<>();
        final ArrayList<Building> buildings=new ArrayList<>();
        final ArrayList<Pickup> pickups=new ArrayList<>();
        volatile float joyX,joyY,aimX,aimY;
        volatile boolean shooting,started,finished,win;
        volatile int healRequests, restartRequests;
        float x=120,z=119,angle=0,hp=100,ammo=30,reserve=150,zone=112,elapsed,fireCd,healCd;
        int kills,kits=2;
        int processedHeal,processedRestart;
        World(){reset();}
        float rnd(float a,float b){return a+random.nextFloat()*(b-a);}
        float distance(float ax,float az,float bx,float bz){return (float)Math.hypot(ax-bx,az-bz);}
        boolean island(float x,float z){
            float u=(x-120)/113f,v=(z-120)/106f;
            return u*u+v*v+.08f*(float)Math.sin(x*.08f)*Math.sin(z*.06f)<1;
        }
        boolean blocked(float px,float pz,float r){
            if(!island(px,pz))return true;
            for(Building b:buildings)if(px>b.x-b.w/2-r&&px<b.x+b.w/2+r&&pz>b.z-b.d/2-r&&pz<b.z+b.d/2+r)return true;
            return false;
        }
        void reset(){
            random.setSeed(2410);buildings.clear();pickups.clear();bots.clear();bullets.clear();
            // Independently designed locations: docks, hangars, village, radio hill and solar farm.
            addDistrict(68,80,4,3,12,12,0);
            addDistrict(160,82,3,2,17,17,1);
            addDistrict(82,163,4,3,11,12,0);
            addDistrict(157,164,3,3,14,13,2);
            for(int i=0;i<24;i++){
                float bx=rnd(37,204),bz=rnd(36,205);
                if(island(bx,bz)&&distance(bx,bz,120,119)>21&&!blocked(bx,bz,8))
                    buildings.add(new Building(bx,bz,rnd(3,6),rnd(3,6),rnd(3,9),3));
            }
            x=120;z=119;angle=0;hp=100;ammo=30;reserve=150;kits=2;kills=0;
            zone=112;elapsed=0;fireCd=0;healCd=0;finished=false;win=false;started=false;
            joyX=joyY=aimX=aimY=0;shooting=false;
            for(int i=0;i<19;i++){
                float bx,bz;int j=0;
                do{bx=rnd(27,212);bz=rnd(27,212);}while((blocked(bx,bz,1.5f)||distance(bx,bz,x,z)<22)&&++j<1500);
                bots.add(new Bot(bx,bz));
            }
            for(int i=0;i<65;i++){
                float bx,bz;int j=0;
                do{bx=rnd(25,215);bz=rnd(25,215);}while(blocked(bx,bz,1.2f)&&++j<1200);
                if(!blocked(bx,bz,1.2f))pickups.add(new Pickup(bx,bz,i%5==0?1:0));
            }
        }
        void addDistrict(float sx,float sz,int cols,int rows,float dx,float dz,int type){
            for(int ix=0;ix<cols;ix++)for(int iz=0;iz<rows;iz++){
                float bx=sx+ix*dx,bz=sz+iz*dz;
                if(distance(bx,bz,120,119)<22)continue;
                buildings.add(new Building(bx,bz,dx*.58f,dz*.55f,type==1?7:5,type));
            }
        }
        void movePlayer(float dx,float dz,float dt){
            float mag=(float)Math.hypot(dx,dz);if(mag>1){dx/=mag;dz/=mag;}
            float nx=x+dx*13*dt,nz=z+dz*13*dt;
            if(!blocked(nx,z,1.1f))x=nx;
            if(!blocked(x,nz,1.1f))z=nz;
            if(mag>.12f)angle=(float)Math.atan2(dx,-dz);
        }
        void shoot(){
            if(ammo<=0){if(reserve>0){float n=Math.min(30,reserve);reserve-=n;ammo=n;}return;}
            ammo--;fireCd=.145f;
            Bot best=null;float nearest=43;
            for(Bot b:bots)if(b.alive){float d=distance(x,z,b.x,b.z);if(d<nearest){nearest=d;best=b;}}
            float dx=(float)Math.sin(angle),dz=-(float)Math.cos(angle);
            if(best!=null){dx=best.x-x;dz=best.z-z;float mag=(float)Math.hypot(dx,dz);dx/=mag;dz/=mag;angle=(float)Math.atan2(dx,-dz);}
            bullets.add(new Bullet(x+dx*1.6f,z+dz*1.6f,dx*42,dz*42,true));
        }
        void update(float dt){
            if(processedRestart!=restartRequests){processedRestart=restartRequests;reset();started=true;}
            if(!started||finished)return;
            dt=Math.min(dt,.045f);elapsed+=dt;
            zone=Math.max(13,112-Math.max(0,elapsed-12)*.36f);
            fireCd-=dt;healCd-=dt;
            movePlayer(joyX,joyY,dt);
            if(Math.abs(aimX)+Math.abs(aimY)>.15f)angle=(float)Math.atan2(aimX,-aimY);
            if(processedHeal!=healRequests){processedHeal=healRequests;if(kits>0&&hp<100&&healCd<=0){kits--;hp=Math.min(100,hp+45);healCd=5;}}
            if(shooting&&fireCd<=0)shoot();
            float zx=x-120,zz=z-120;
            if(Math.hypot(zx,zz)>zone)hp-=10*dt;
            for(Bot b:bots){
                if(!b.alive)continue;
                float px=x-b.x,pz=z-b.z,d=(float)Math.hypot(px,pz);
                if(d>0.1f){
                    float dx=0,dz=0;
                    if(d<51&&d>11){dx=px/d;dz=pz/d;}
                    else if(d<9){dx=-px/d;dz=-pz/d;}
                    float vz=b.z-120,vx=b.x-120;
                    if(Math.hypot(vx,vz)>zone-6){float m=(float)Math.hypot(vx,vz);dx=-vx/m;dz=-vz/m;}
                    float nx=b.x+dx*5.3f*dt,nz=b.z+dz*5.3f*dt;
                    if(!blocked(nx,b.z,1))b.x=nx;
                    if(!blocked(b.x,nz,1))b.z=nz;
                    b.theta=(float)Math.atan2(px,-pz);
                    b.cd-=dt;
                    if(d<37&&b.cd<=0){
                        float deviation=rnd(-.14f,.14f),a=b.theta+deviation;
                        bullets.add(new Bullet(b.x+(float)Math.sin(a),b.z-(float)Math.cos(a),(float)Math.sin(a)*29,-(float)Math.cos(a)*29,false));
                        b.cd=rnd(.65f,1.25f);
                    }
                }
                if(distance(b.x,b.z,120,120)>zone)b.hp-=8*dt;
                if(b.hp<=0)b.alive=false;
            }
            for(int i=bullets.size()-1;i>=0;i--){
                Bullet s=bullets.get(i);s.life-=dt;
                float nx=s.x+s.dx*dt,nz=s.z+s.dz*dt;
                if(s.life<=0||blocked(nx,nz,.15f)){bullets.remove(i);continue;}
                s.x=nx;s.z=nz;
                if(s.friendly){
                    boolean hit=false;
                    for(Bot b:bots)if(b.alive&&distance(s.x,s.z,b.x,b.z)<1.5f){b.hp-=26;if(b.hp<=0){b.alive=false;kills++;}hit=true;break;}
                    if(hit)bullets.remove(i);
                }else if(distance(s.x,s.z,x,z)<1.25f){hp-=9;bullets.remove(i);}
            }
            for(Pickup p:pickups)if(!p.taken&&distance(x,z,p.x,p.z)<2.6f){p.taken=true;if(p.type==1)kits++;else reserve+=35;}
            if(hp<=0){hp=0;finished=true;win=false;shooting=false;}
            int alive=0;for(Bot b:bots)if(b.alive)alive++;
            if(alive==0){finished=true;win=true;shooting=false;}
        }
        int alive(){int n=1;for(Bot b:bots)if(b.alive)n++;return n;}
    }

    private final class Scene implements GLSurfaceView.Renderer {
        private final float[] proj=new float[16],view=new float[16],vp=new float[16],model=new float[16],mvp=new float[16];
        private FloatBuffer cube;
        private int program,posLoc,mvpLoc,colLoc,lightLoc;
        private long last=System.nanoTime();
        private final float[] green={.28f,.49f,.27f,1},sand={.72f,.68f,.42f,1},sea={.13f,.43f,.60f,1};
        private final float[] roof={.51f,.24f,.17f,1},concrete={.52f,.54f,.51f,1},metal={.36f,.50f,.55f,1};
        private final float[] black={.12f,.16f,.18f,1},white={.83f,.85f,.72f,1};
        private final float[] red={.85f,.18f,.13f,1},blue={.13f,.50f,.87f,1},yellow={.93f,.78f,.22f,1};
        private int shader(int type,String code){int sh=GLES20.glCreateShader(type);GLES20.glShaderSource(sh,code);GLES20.glCompileShader(sh);return sh;}
        @Override public void onSurfaceCreated(GL10 unused,EGLConfig config){
            GLES20.glClearColor(.57f,.78f,.88f,1);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            int vs=shader(GLES20.GL_VERTEX_SHADER,"uniform mat4 uMVP;attribute vec3 aPos;varying float shade;void main(){gl_Position=uMVP*vec4(aPos,1.0);shade=.8+.2*aPos.y;}");
            int fs=shader(GLES20.GL_FRAGMENT_SHADER,"precision mediump float;uniform vec4 uColor;varying float shade;void main(){gl_FragColor=vec4(uColor.rgb*shade,uColor.a);}");
            program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);
            posLoc=GLES20.glGetAttribLocation(program,"aPos");mvpLoc=GLES20.glGetUniformLocation(program,"uMVP");colLoc=GLES20.glGetUniformLocation(program,"uColor");
            float[] v={
                // Each face has counterclockwise triangle winding seen from outside.
                -.5f,-.5f,.5f, .5f,-.5f,.5f, .5f,.5f,.5f, -.5f,-.5f,.5f, .5f,.5f,.5f, -.5f,.5f,.5f,
                .5f,-.5f,-.5f, -.5f,-.5f,-.5f, -.5f,.5f,-.5f, .5f,-.5f,-.5f, -.5f,.5f,-.5f, .5f,.5f,-.5f,
                -.5f,-.5f,-.5f, -.5f,-.5f,.5f, -.5f,.5f,.5f, -.5f,-.5f,-.5f, -.5f,.5f,.5f, -.5f,.5f,-.5f,
                .5f,-.5f,.5f, .5f,-.5f,-.5f, .5f,.5f,-.5f, .5f,-.5f,.5f, .5f,.5f,-.5f, .5f,.5f,.5f,
                -.5f,.5f,.5f, .5f,.5f,.5f, .5f,.5f,-.5f, -.5f,.5f,.5f, .5f,.5f,-.5f, -.5f,.5f,-.5f,
                -.5f,-.5f,-.5f, .5f,-.5f,-.5f, .5f,-.5f,.5f, -.5f,-.5f,-.5f, .5f,-.5f,.5f, -.5f,-.5f,.5f
            };
            ByteBuffer bb=ByteBuffer.allocateDirect(v.length*4).order(ByteOrder.nativeOrder());cube=bb.asFloatBuffer();cube.put(v).position(0);
        }
        @Override public void onSurfaceChanged(GL10 unused,int width,int height){
            GLES20.glViewport(0,0,width,height);
            Matrix.perspectiveM(proj,0,58,(float)width/Math.max(1,height),.5f,340);
        }
        private void draw(float x,float y,float z,float sx,float sy,float sz,float[] color){
            Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);Matrix.scaleM(model,0,sx,sy,sz);
            Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);
            GLES20.glUniform4fv(colLoc,1,color,0);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36);
        }
        private void person(float x,float z,float facing,boolean enemy){
            draw(x,1.05f,z,1.0f,1.55f,.8f,enemy?red:blue);
            draw(x,2.13f,z,.65f,.68f,.65f,white);
            draw(x-.3f,.3f,z,.34f,.65f,.35f,black);
            draw(x+.3f,.3f,z,.34f,.65f,.35f,black);
            draw(x+(float)Math.sin(facing)*.48f,1.45f,z-(float)Math.cos(facing)*.48f,.28f,.27f,1.5f,black);
        }
        @Override public void onDrawFrame(GL10 unused){
            long now=System.nanoTime();float dt=(now-last)/1e9f;last=now;
            world.update(dt);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glUseProgram(program);GLES20.glEnableVertexAttribArray(posLoc);
            cube.position(0);GLES20.glVertexAttribPointer(posLoc,3,GLES20.GL_FLOAT,false,12,cube);
            float cx=world.x,cz=world.z;
            Matrix.setLookAtM(view,0,cx,24,cz+29,cx,1.1f,cz-5,0,1,0);
            Matrix.multiplyMM(vp,0,proj,0,view,0);
            draw(120,-.65f,120,258,1,258,sea);
            // Island: overlapping terrain tiles approximate a tropical coastline without an external mesh.
            for(int ix=0;ix<24;ix++)for(int iz=0;iz<24;iz++){
                float x=5+ix*10,z=5+iz*10;
                if(world.island(x,z)){
                    boolean coast=!world.island(x+7,z)||!world.island(x-7,z)||!world.island(x,z+7)||!world.island(x,z-7);
                    draw(x,-.13f,z,10,.35f,10,coast?sand:green);
                }
            }
            // Roads and the central landing strip; district geometry is original.
            draw(120,.075f,119,8,.08f,113,concrete);
            draw(110,.08f,119,4,.08f,63,black);
            draw(120,.08f,121,108,.07f,4,concrete);
            for(Building b:world.buildings){
                draw(b.x,b.h*.5f,b.z,b.w,b.h,b.d,b.type==1?metal:(b.type==3?concrete:white));
                draw(b.x,b.h+.23f,b.z,b.w+.5f,.46f,b.d+.5f,b.type==1?black:roof);
            }
            // Palm trees distributed around the island; deterministic positions.
            for(int i=0;i<110;i++){
                float tx=25+((i*71)%190),tz=25+((i*113)%190);
                if(!world.island(tx,tz)||!world.island(tx+5,tz)||world.distance(tx,tz,120,119)<14||world.blocked(tx,tz,2.4f))continue;
                draw(tx,2.5f,tz,.65f,5,.65f,roof);
                draw(tx,5.3f,tz,4,.7f,4,green);
                draw(tx,6,tz,2.7f,.6f,2.7f,green);
            }
            for(Pickup p:world.pickups)if(!p.taken)draw(p.x,.55f,p.z,1,1,1,p.type==1?red:yellow);
            for(Bot b:world.bots)if(b.alive)person(b.x,b.z,b.theta,true);
            if(world.hp>0)person(world.x,world.z,world.angle,false);
            for(Bullet b:world.bullets)draw(b.x,1.15f,b.z,.3f,.25f,.55f,b.friendly?yellow:red);
            // Four visible markers make the shrinking-zone boundary legible in 3D.
            for(int i=0;i<72;i++){
                double a=i*Math.PI*2/72;float bx=120+(float)Math.cos(a)*world.zone,bz=120+(float)Math.sin(a)*world.zone;
                if(world.island(bx,bz))draw(bx,.55f,bz,1.2f,1.1f,1.2f,blue);
            }
        }
    }

    private final class Controls extends View {
        private final Paint p=new Paint(3);
        private int movePointer=-1,aimPointer=-1;
        private float moveCenterX,moveCenterY,moveEndX,moveEndY;
        Controls(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void label(Canvas c,String s,float x,float y,float size,int color){
            p.setColor(color);p.setTextSize(size);p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            p.setShadowLayer(3,1,2,Color.BLACK);c.drawText(s,x,y,p);p.clearShadowLayer();
        }
        void disk(Canvas c,float x,float y,float r,int color){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,r,p);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float w=getWidth(),h=getHeight(),u=Math.min(w/1000f,h/600f);
            if(u<=0)return;
            label(c,"LAST CIRCLE 3D",23*u,35*u,24*u,Color.WHITE);
            label(c,"VIDA: "+(int)world.hp+"   VIVOS: "+world.alive()+"   BAJAS: "+world.kills,23*u,64*u,19*u,Color.WHITE);
            label(c,"MUNICION: "+world.ammo+" / "+world.reserve+"   BOTIQUINES: "+world.kits,23*u,90*u,17*u,Color.WHITE);
            label(c,"ZONA: "+(int)world.zone+" m",w-176*u,36*u,17*u,Color.CYAN);
            disk(c,105*u,h-105*u,69*u,0x66444f55);
            disk(c,105*u+world.joyX*36*u,h-105*u+world.joyY*36*u,28*u,0xaaeeeeee);
            disk(c,w-108*u,h-105*u,66*u,0x99a42323);
            label(c,"FUEGO",w-140*u,h-98*u,22*u,Color.WHITE);
            disk(c,w-108*u,h-252*u,44*u,0x995b9949);
            label(c,"CURA",w-137*u,h-244*u,18*u,Color.WHITE);
            if(!world.started||world.finished){
                p.setColor(0xd900151b);c.drawRect(0,0,w,h,p);
                String title=!world.started?"LAST CIRCLE 3D":world.win?"¡VICTORIA!":"ELIMINADO";
                label(c,title,w/2-155*u,h/2-80*u,42*u,Color.WHITE);
                label(c,"Isla tropical original · 20 combatientes · Sin conexion",w/2-250*u,h/2-25*u,19*u,Color.WHITE);
                p.setColor(0xff44c879);c.drawRoundRect(w/2-115*u,h/2+12*u,w/2+115*u,h/2+84*u,15*u,15*u,p);
                label(c,world.started?"REINTENTAR":"JUGAR",w/2-67*u,h/2+60*u,28*u,Color.BLACK);
            }
            postInvalidateDelayed(80);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked(),index=e.getActionIndex(),id=e.getPointerId(index);
            float w=getWidth(),h=getHeight(),u=Math.min(w/1000f,h/600f);
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
                float tx=e.getX(index),ty=e.getY(index);
                if(!world.started||world.finished){
                    if(tx>w/2-150*u&&tx<w/2+150*u&&ty>h/2-10*u&&ty<h/2+115*u){
                        world.restartRequests++;world.started=true;
                    }
                    return true;
                }
                if(tx<w*.45f&&ty>h*.48f&&movePointer==-1){movePointer=id;moveCenterX=tx;moveCenterY=ty;}
                else if(tx>w-175*u&&ty>h-185*u){world.shooting=true;aimPointer=id;moveEndX=tx;moveEndY=ty;}
                else if(tx>w-175*u&&ty>h-310*u&&ty<h-195*u)world.healRequests++;
                else if(tx>w*.45f&&aimPointer==-1){aimPointer=id;moveEndX=tx;moveEndY=ty;world.shooting=true;}
            }else if(action==MotionEvent.ACTION_MOVE){
                for(int i=0;i<e.getPointerCount();i++){
                    int pointer=e.getPointerId(i),tx=(int)e.getX(i),ty=(int)e.getY(i);
                    if(pointer==movePointer){world.joyX=Math.max(-1,Math.min(1,(tx-moveCenterX)/(75*u)));world.joyY=Math.max(-1,Math.min(1,(ty-moveCenterY)/(75*u)));}
                    else if(pointer==aimPointer){float dx=tx-moveEndX,dy=ty-moveEndY;
                        if(Math.hypot(dx,dy)>16*u){float mag=(float)Math.hypot(dx,dy);world.aimX=dx/mag;world.aimY=dy/mag;}
                    }
                }
            }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_CANCEL){
                if(id==movePointer||action==MotionEvent.ACTION_CANCEL){movePointer=-1;world.joyX=world.joyY=0;}
                if(id==aimPointer||action==MotionEvent.ACTION_CANCEL){aimPointer=-1;world.shooting=false;world.aimX=world.aimY=0;}
            }
            return true;
        }
    }
}
