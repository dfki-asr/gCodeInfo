package de.dietzm.gcodesim;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;

import de.dietzm.SerialIO;


public class AWTGraphicRenderer implements GraphicRenderer {

	Color[] backcol = new Color[]{Color.BLACK,new Color(0,90,120),Color.red};
	final Color[] colors = new Color[] { Color.red, Color.cyan, Color.yellow, Color.magenta, Color.green,
			Color.orange, Color.pink, Color.white, Color.darkGray };

	Frame frame;

	Graphics2D g = null;
	private final GraphicsConfiguration gfxConf = GraphicsEnvironment.getLocalGraphicsEnvironment()
			.getDefaultScreenDevice().getDefaultConfiguration();
	BufferedImage offimg , offimg2;
	int[] pos = new int[5];
	SerialIO sio = null;
	String titletxt = null;	
	long titletime=0;

	// Color[] colors = new Color[] { new Color(0xffAAAA), new Color(0xffBAAA),
	// new Color(0xffCAAA), new Color(0xffDAAA),
	// new Color(0xffEAAA), new Color(0xffFAAA), new Color(0xffFFAA) ,
	// Color.white, Color.darkGray};
	// Stroke 0=travel 1=print
	private BasicStroke stroke[] = {
			new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[] { 1, 2 }, 0),
			new BasicStroke(3.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND),
			new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[] { 1, 6 }, 0) };

	public AWTGraphicRenderer(int bedsizeX, int bedsizeY, Frame frame) {
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		int wmax = (int)Math.min(d.getWidth(),1920);
		int hmax = (int)(Math.min(d.getHeight(),1200));
		//System.out.println("Window: w:"+wmax+" h:"+hmax);
		offimg = gfxConf.createCompatibleImage(wmax, hmax);
		offimg2= gfxConf.createCompatibleImage(wmax,hmax);
		g = (Graphics2D) offimg.getGraphics();
		g.setBackground(Color.black);
		g.setFont(Font.decode(Font.SANS_SERIF));
		this.frame = frame;

		// Experiment with more colors
		// colors = new Color[302];
		// int rgb = 0x994444;
		// for (int i = 0; i < colors.length; i++) {
		// colors[i]=new Color(rgb);
		// System.out.println(rgb);
		// if(i % 2 == 0){
		// rgb+=0x000005;
		// }else{
		// rgb+=0x000500;
		// }
		// }
		// colors[colors.length-2]=Color.white;
		// colors[colors.length-1]=Color.darkGray;

	}

	@Override
	public String browseFileDialog() {
		String var = GcodeSimulator.openFileBrowser(frame);
		frame.requestFocus();
		return var;
	}

	@Override
	public void clearrect(float x, float y, float w, float h,int colitem) {
		g.setBackground(backcol[colitem]);
		g.clearRect((int) x, (int) y, (int) w, (int) h);
	}
	
	public void faintRect(float x,float y, float w,float h){
		//TODO
	}

	/**
	 * Clone the image to avoid problems with multithreading (EDT vs
	 * GCodePainter thread)
	 */
	@SuppressWarnings("unused")
	private void cloneImage() {
		// ColorModel cm = offimg.getColorModel();
		// boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
		// WritableRaster raster = offimg.copyData(null);
		// offimg2 = new BufferedImage(cm, raster, isAlphaPremultiplied, null);
		// BufferedImage currentImage = new
		// BufferedImage(width,height,BufferedImage.TYPE_3BYTE_BGR);

		// Fastest method to clone the image (DOES NOT WORK for MACOS)
		// int[] frame =
		// ((DataBufferInt)offimg.getRaster().getDataBuffer()).getData();
		// int[] imgData =
		// ((DataBufferInt)offimg2.getRaster().getDataBuffer()).getData();
		// System.arraycopy(frame,0,imgData,0,frame.length);
	}

	public void drawArc(int x, int y, int width, int height, int theta, int delta) {
		g.drawArc(x, y, width, height, theta, delta);
	}
	public int getColorNr(){
		return colors.length;
	}

	/**
	 * Draw the buffer to the real surface
	 * @param gp
	 */
	public synchronized void drawImage(Graphics gp1) {

		Graphics2D gp = (Graphics2D) offimg2.getGraphics();
		
		gp.drawImage(offimg, 0, 0, frame);

		if(getPos()[0] != 0){
			// Paint current print point (nozzle)
			gp.setColor(g.getColor());
			gp.fillOval((int) getPos()[0] + 4, (int) getPos()[1] , 4, 4);
			gp.setColor(Color.white);
			gp.drawOval((int) getPos()[0] - 2, (int) getPos()[1] -6, 16, 16);
			gp.drawOval((int) getPos()[0] + 0, (int) getPos()[1] -4, 12, 12);
			gp.drawOval((int) getPos()[0] + 2, (int) getPos()[1] -2, 8, 8);
	
		//	paintExtruder(gp,getPos()[2],getPos()[4]);
			paintExtruder(gp,getPos()[3],getPos()[2],getPos()[4]);

		}
		
		//gp.drawOval((int) getPos()[2] + 2, (int) getPos()[4] + 51, 8, 8);
		//gp.drawOval((int) getPos()[3] + 2, (int) getPos()[4] + 51, 8, 8);
		
		//Blinking mode "simulation" or "print"
		// gp.drawOval((int)getPos()[0]+4,(int)getPos()[1]+53,4,4);
		if(titletxt!=null && System.currentTimeMillis()-titletime > 1500){
			if(System.currentTimeMillis()-titletime > 3000){
			titletime = System.currentTimeMillis();
			}
			gp.setColor(Color.GREEN);
			gp.setFont(gp.getFont().deriveFont(26f));
			gp.drawString(titletxt, 20 ,90);
		}
		gp1.drawImage(offimg2, 0,0, frame);
	}

	private void paintExtruder(Graphics2D gp, int pos,int poss, int zpos) {
//		gp.setColor(Color.gray);
//		gp.fillRect(730, zpos + 51-15, 250, 3);
//		
		//side&front Extruder
		gp.setColor(Color.white);
		gp.fillRect(pos + 2, zpos -25, 15, 20); //hotend
		gp.drawLine(pos+2, zpos -5, pos+2+7, zpos ); //hotend
		gp.drawLine(pos+2+14, zpos -5, pos+2+7, zpos); //hotend
		
		 //Extruder
		gp.fillRect(pos + 2-13, zpos -45, 46, 28); 
		 //gears	
		gp.setColor(Color.lightGray);
		gp.fillOval(pos + 2+2, zpos -53, 35, 35);//Large gear
		gp.fillOval(pos -6, zpos -41, 11, 11); //small gear
		
//		gp.drawOval(pos + 2+3, zpos + 51-49, 33, 33);
//		gp.drawOval(pos + 2+4, zpos + 51-48, 31, 31);
		gp.setColor(Color.white);
		gp.fillOval(pos + 2+14, zpos -41, 11, 11);
//		gp.drawOval(pos + 2+15, zpos + 51-37, 9, 9);		
		gp.fillOval(pos -2, zpos -37, 3, 3);
		
		gp.fillOval(poss+2, zpos -1, 3, 3); //sideview
		//Filament
		gp.setColor(g.getColor());
		gp.drawArc(pos-185, zpos-130, 190, 190, 0, 40);
	}

	@Override
	public void drawline(float x, float y, float x1, float y1) {
		g.draw(new Line2D.Float(x, y, x1, y1));
	}

	@Override
	public void drawrect(float x, float y, float w, float h) {
		g.drawRect((int) x, (int) y, (int) w, (int) h);
	}

	@Override
	public void drawtext(CharSequence text, float x, float y) {
		// g.getFontMetrics();
		g.drawString(text.toString(), x, y);
	}

	public void drawtext(CharSequence text, float x, float y, float w) {
		String txt=text.toString();
		int wide = g.getFontMetrics().stringWidth(txt);
		float center = (w - wide) / 2;
		g.drawString(txt, x + center, y);

	}

	@Override
	public void fillrect(float x, float y, float w, float h) {
		g.fillRect((int) x, (int) y, (int) w, (int) h);
	}

	public int getHeight() {
		return offimg.getHeight();
	}

	public synchronized int[] getPos() {
		return pos;
	}

	public int getWidth() {
		return offimg.getWidth();
	}




	public synchronized void repaint() {
		// cloneImage();
		frame.repaint();

		// try {
		// //Wait for repaint to happen to avoid changing the image in between.
		// (causes flickering)
		// wait(10);
		// } catch (InterruptedException e) {
		// }
	}

	public void setColor(int idx) {
		g.setColor(colors[idx]);
	}

	public void setFontSize(float font) {
		g.setFont(g.getFont().deriveFont(font));
	}

	public synchronized void setPos(int x, int y) {
		// System.out.println("POS: X:"+x+" Y:"+y );
		pos[0] = x;
		pos[1] = y;
	}
	
	/**
	 * Set Position for the side & Front view
	 * @param x1
	 * @param x2
	 * @param z
	 */
	@Override
	public void setPos(int x1,int x2,int z) {
		pos[2] = x1;
		pos[3] = x2;
		pos[4] = z;
	}

	public void setStroke(int idx) {
		g.setStroke(stroke[idx]);
	}
	
	public void setTitle(String txt){
		titletxt=txt;
	}
}
