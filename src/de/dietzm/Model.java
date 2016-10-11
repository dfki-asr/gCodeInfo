package de.dietzm;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

import de.dietzm.Layer.Speed;
import de.dietzm.gcodes.GCode;
import de.dietzm.gcodes.GCodeFactory;
import de.dietzm.gcodes.GCodeStore;

public class Model {

	private float PRICE_PER_G=30/1000f; //Euro per gram 
	private float PRICE_PER_H=0; //Price per hour operation


	private boolean isguessed=false; 
	private boolean useAcceleration=true;
	private static boolean ACCELERATION = true;
	private float avgbedtemp=0,avgextemp=0;
	private float avgLayerHeight = 0;
	private float avgspeed=0,avgtravelspeed=0,maxprintspeed=0,minprintspeed=Float.MAX_VALUE;
	private float boundaries[] = { 0, 9999, 0, 9999, 0 }; // Xmax,Xmin,Ymax,Ymin,Zmax
	private float dimension[] = { 0, 0, 0 }; // X,y,z
	private float extrusion=0;
	private int extruderCount = 1;
	private String filename;
	private String uri = null;
	private InputStream in=null;
	private String currency = "€";
	private GCodeStore gcodes;// = GCodeFactory.getGcodeStore(1);
	//private SortedMap<Float, Layer> layer = new TreeMap<Float, Layer>();
	private ArrayList<Layer> layer = new ArrayList<Layer>();
	private int layercount = 0, notprintedLayers = 0;
	private SortedMap<Float, SpeedEntry> SpeedAnalysisT = new TreeMap<Float, SpeedEntry>();
	private float distance,traveldistance,timeaccel;
	private String unit = "mm"; //default is mm
	public enum Material {PLA,ABS,NYLON,PVA,UNKNOWN};
	private long filesize=0;
	boolean relativepos = false;
	public Position[] extruderOffset = null; //TODO make it configurable
	private float price = 0;
	private float weight=0;
	private float mass=0;
	private float diameter=0;
	
	private Material material=null;

	byte[] imgdata = null;

	/**
	 * Thumbnail
	 * @return
	 */
	public byte[] getImgdata() {
		return imgdata;
	}
	public void overwriteMaterial(Material material) {
		this.material = material;
		weight=0;
		mass=0;
		price=0;
	}
	public void overwritePricePerG(float pRICE_PER_G) {
		PRICE_PER_G = pRICE_PER_G;
		price=0;
	}
	
	public void overwritePricePerH(float pRICE_PER_H) {
		PRICE_PER_H = pRICE_PER_H;
		price=0;
	}
	
	public void overwriteDiameter(float diameter) {
		this.diameter = diameter;
		weight=0;
		mass=0;
		price=0;
	}
	
	public InputStream getInputStream(){
		return in;
	}
	public void setImgdata(byte[] imgdata) {
		this.imgdata = imgdata;
	}
	public Position[] getExtruderOffset() {
		return extruderOffset;
	}
	public int getExtruderCount() {
		return extruderCount;
	}
	
	public float getDiameter(){
		if(diameter == 0){
			diameter = guessDiameter();
		}
		return diameter;
	}
	
	public Material getMaterial(){
		if(material == null){
			material = guessMaterial();
		}
		return material;
	}
	public float getWeight(){
		if(weight == 0){
			calculateFilamentUsage();
		}
		return weight;
	}
	/**
	 * Mass in mm3
	 * @return
	 */
	public float getMass(){
		if(mass == 0){
			calculateFilamentUsage();
		}
		return mass;
	}
	
	public float getPrice() {
		if(price == 0){
			calculateFilamentUsage();
		}
		return price;
	}
	private void calculateFilamentUsage() {
		getDiameter(); //initialize diameter
		getMaterial(); //initialize material
		
		mass = (float)((diameter/2)*(diameter/2)*Math.PI*getExtrusion());
		
		switch (material) {
		case PLA:
			weight= mass*0.00125f;
			break;
		case ABS:
			weight= mass*0.00105f;
			break;
		case NYLON:
			weight= mass*0.001134f;
			break;
		case PVA:
			weight= mass*0.00123f;
			break;
		default:
			break;
		}
		
		//Read user defined environment variable if exists
		float price_per_g= PRICE_PER_G;
				try {
					String prc = System.getenv("FILAMENT_PRICE");
					if(prc != null){
						//System.out.println("Use env value FILAMENT_DIAMETER="+dia);
						price_per_g = Float.parseFloat(prc);
					}
				} catch (NumberFormatException e1) {
				}
		
		
		price = weight*price_per_g + (PRICE_PER_H*(getTimeaccel()/3600f));
		//System.out.println("PRICE PER H:"+ (PRICE_PER_H*(getTimeaccel()/3600f)) +" - "+ PRICE_PER_H +" - "+ getTimeaccel());
		
	}
	
	public long getFilesize() {
		return filesize;
	}
	public long getReadbytes() {
		return GCodeFactory.getReadBytes();
	}
	public long getReadLines() {
		return GCodeFactory.getReadLines();
	}
	public Model(String file)  {
		this.filename = file;
		//read env variables
		try {
			String kgprice = System.getenv("FILAMENT_PRICE_KG");
			if(kgprice != null){
				PRICE_PER_G=Float.parseFloat(kgprice)/1000;
			}
		} catch (NumberFormatException e) {
			//Use default price (30€)
		}
	}
	
	public Model(String name, String uri){
		this(name);
		this.uri=uri;
	}
	
	public Model(InputStream in, String name, String uri){
		this(name);
		this.uri=uri;
		this.in=in;
	}
	
	public Model(String file, GCodeStore gcall){
		this(file);
		gcodes=gcall;		
	}
	/**
	 * Main method to walk through the GCODES and analyze them. 
	 * @return
	 */
	public void analyze() {
		Layer currLayer = startLayer(0,null);
		Layer lastprinted =currLayer;
		//Current Positions & Speed
		float xpos=0;
		Position currpos = new Position(0, 0);
		float lastxpos=0;
		float lastypos=0;
		float lastzpos=0;
		boolean pos_changed=false;
		float ypos=0;
		float zpos=0;
		float epos=0;
		float f_old=1000;
		float f_new=f_old;
		float bedtemp=0, extemp=0;
		boolean m101=false; //BFB style extrusion
		float m108=0; //Bfb style extr.

	
		GCodeStore gcarr = getGcodes();
		int gcnum = gcarr.size();
		for(int ig = 0 ; ig < gcnum; ig++ ){
			GCode gc = gcarr.get(ig);
			lastxpos=xpos;
			lastypos=ypos;
			lastzpos=zpos;
	
			//Update Speed if specified
			//TODO Clarify if default speed is the last used speed or not
			if(gc.isInitialized(Constants.F_MASK)){
				if(!gc.isInitialized(Constants.X_MASK) && !gc.isInitialized(Constants.Y_MASK) && !gc.isInitialized(Constants.Z_MASK) || !ACCELERATION){
					f_old=gc.getF(); //no movement no acceleration
					f_new=gc.getF(); //faccel is the same
				}else{
					f_new=gc.getF(); //acceleration
				}
			}
			
			if (gc.getGcode() == Constants.GCDEF.G1 || gc.getGcode() == Constants.GCDEF.G0 || gc.getGcode() == Constants.GCDEF.G2 || gc.getGcode() == Constants.GCDEF.G3) {
				if(currLayer.highidx - currLayer.lowidx < 5){ //only set layer temp, if not already printed too much 
					currLayer.setBedtemp(bedtemp);
					currLayer.setExttemp(extemp);
				}
				//Detect Layer change and create new layers.
				if(gc.isInitialized(Constants.Z_MASK) && gc.getZ() != currLayer.getZPosition()){
					//endLayer(currLayer);	//finish old layer
					if(currLayer.isPrinted()){
						lastprinted=currLayer;
					}else if(lastprinted!=currLayer){
						//Assume zlift
						//Append non printed layers to last printed one 
						//Z-lift would otherwise cause thousands of layers
//						for (GCode gco : currLayer.getGcodes()) {
//							lastprinted.addGcodes(gco,ig);
//						}
						lastprinted.highidx=ig; //set high index to new index
						layercount--;
						layer.remove(currLayer);
						//Minor problem is that the beginning of a new layer is sometimes without extrusion before the first z-lift
						//this leads to assigning this to the previous printed layer. 
					}					
					currLayer = startLayer(gc.getZ(),lastprinted);//Start new layer
					currLayer.setBedtemp(bedtemp);
					currLayer.setExttemp(extemp);
				}	
				float move = 0;
				//Move G1 - X/Y at the same time
				if (gc.getGcode() == Constants.GCDEF.G2 || gc.getGcode() == Constants.GCDEF.G3){
					//center I&J relative to x&y
					float cx = (xpos+gc.getIx());
					float cy = (ypos+gc.getJy());
					float newxpos = gc.isInitialized(Constants.X_MASK) ? gc.getX():xpos;
					float newypos = gc.isInitialized(Constants.Y_MASK) ? gc.getY():ypos;
					//triangle
					float bx=(newxpos-cx);
					float by=(newypos-cy);
					float ax=(xpos-cx);
					float ay=(ypos-cy);
					//Java drawarc is based on a bonding box
					//Left upper edge of the bounding box
					float xmove = Math.abs(cx-xpos);
					float ymove = Math.abs(cy-ypos);
					//assume a circle (no oval)
					float radius = ((float) Math.sqrt((xmove * xmove) + (ymove * ymove)));
					double angle1 ,angle2 ;
					//Calculate right angle
					if(gc.getGcode() == Constants.GCDEF.G2){
						angle1 = Math.atan2(by,bx) * (180/Math.PI);
						angle2 = Math.atan2(ay,ax) * (180/Math.PI);
					}else{
						angle2 = Math.atan2(by,bx) * (180/Math.PI);
						angle1 = Math.atan2(ay,ax) * (180/Math.PI);
					}
					double angle=(int) (angle2-angle1);
					
					xpos=newxpos;
					ypos=newypos;
					//Bogenlaenge
					move = (float) (Math.PI * radius * angle / 180);
					gc.setDistance(move);
				}else if (gc.isInitialized(Constants.X_MASK) && gc.isInitialized(Constants.Y_MASK)) {
					float xmove = Math.abs(xpos - gc.getX());
					float ymove = Math.abs(ypos - gc.getY());
					xpos = gc.getX();
					ypos = gc.getY();
					move = (float) Math.sqrt((xmove * xmove) + (ymove * ymove));
					gc.setDistance(move);
				} else if (gc.isInitialized(Constants.X_MASK)) {
					move = Math.abs(xpos - gc.getX());
					xpos = gc.getX();
					gc.setDistance(move);
				} else if (gc.isInitialized(Constants.Y_MASK)) {
					move = Math.abs(ypos - gc.getY());
					ypos = gc.getY();
					gc.setDistance(move);
				} else if (gc.isInitialized(Constants.E_MASK)) {
					//Only E means we need to measure the time
					move = Math.abs(epos - gc.getE());
				} else	if (gc.isInitialized(Constants.Z_MASK)) {
					//Only Z means we need to measure the time
					//TODO if Z + others move together, Z might take longest. Need to add time 
					move = Math.abs(zpos - gc.getZ());
					
				}	
				//update Z pos when Z changed 
				if (gc.isInitialized(Constants.Z_MASK)) {
					zpos = gc.getZ();
				}
				//Update epos and extrusion, not add time because the actual move time is already added
				 if(gc.isInitialized(Constants.E_MASK)){
					 	if(relativepos){
					 		gc.setExtrusion(gc.getE());
					 		epos=0;
					 	}else{
					 		float oldepos=gc.getE();
					 		gc.setExtrusion(gc.getE()-epos);
					 		epos=oldepos;
					 	}
				 }else if(m101){
					  float extr = m108 / 60 * (move / (f_new / 60)); //only for direct drive extr. with r=5
					 	//gc.setInitialized(Constants.E_MASK, extr); //commented out to avoid E to be send to the printer
					 	gc.setExtrusion(extr);
				 }
				 
				 
				 if(useAcceleration){
					 //Calculate time with a linear acceleration 
					 if(f_new >= f_old){
						 //Assume sprinter _MAX_START_SPEED_UNITS_PER_SECOND {40.0,40.0,....}
						 gc.setTimeAccel(move / (((Math.min(40*60,f_old)+f_new)/2) / 60)); //set time with linear acceleration
						 //System.out.println("F"+f_old+"FA"+f_new+"time"+gc.getTime()+"ACCEL: "+(Math.abs(40-f_new)/gc.getTimeAccel()));
				 	}else{
				 		gc.setTimeAccel(move / ((f_old+f_new)/2 / 60)); //set time with linear acceleration
				 		//System.out.println("F"+f_old+"FA"+f_new+"  DEACCEL: "+(Math.abs(f_old-f_new)/gc.getTimeAccel()));
				 	}
				 }else{
					 //Calculate time without acceleration
					 gc.setTimeAccel(move / (f_new / 60)); //Set time w/o acceleration
				 }
				 f_old=f_new; //acceleration done. assign new speed
					
				 //Calculate print size
				 //if(gc.isInitialized(Constants.E_MASK) && gc.getE() > 0) {
				 if((gc.isExtruding() && gc.getDistance() != 0) || m101) {
					 if(pos_changed){ //make sure that the start position is used for the boundary calculation
						 currLayer.addPosition(lastxpos,lastypos,lastzpos);
					 }
					currLayer.addPosition(xpos, ypos,zpos);				
					pos_changed=true;
				 }
			}else if(gc.getGcode() == Constants.GCDEF.G28 || gc.getGcode() == Constants.GCDEF.G92){ 	//Initialize Axis
					if(gc.isInitialized(Constants.E_MASK)) epos=gc.getE();
					if(gc.isInitialized(Constants.X_MASK)) xpos=gc.getX();
					if(gc.isInitialized(Constants.Y_MASK)) ypos=gc.getY();
					if(gc.isInitialized(Constants.Z_MASK)) zpos=gc.getZ();
			}else if(gc.getGcode() == Constants.GCDEF.G91){ 
					relativepos = true;
			}else if(gc.getGcode() == Constants.GCDEF.G90){ 
					relativepos = false;
			}else if(gc.getGcode() == Constants.GCDEF.G20 || gc.getGcode() == Constants.GCDEF.G21){ 			//Assume that unit is only set once
				currLayer.setUnit(gc.getUnit());
			}else if(gc.getGcode() == Constants.GCDEF.M101){ //bfb style gcode
				m101=true;
			}else if(gc.getGcode() == Constants.GCDEF.M103){
				m101=false;
			}else if(gc.getGcode() == Constants.GCDEF.T0){
				//extruders=Math.max(extruders, 1); 
			}else if(gc.getGcode() == Constants.GCDEF.T1){
				extruderCount=Math.max(extruderCount, 2);
			}else if(gc.getGcode() == Constants.GCDEF.T2){
				extruderCount=Math.max(extruderCount, 3);
			}else if(gc.getGcode() == Constants.GCDEF.T3){
				extruderCount=Math.max(extruderCount, 4);
			}else if(gc.getGcode() == Constants.GCDEF.T4){
				extruderCount=Math.max(extruderCount, 5);
			}else if(gc.getGcode() == Constants.GCDEF.M218 || gc.getGcode() == Constants.GCDEF.G10){
				float xoff = 0;
				float yoff = 0;
				if(gc.isInitialized(Constants.X_MASK)) xoff = gc.getX();
				if(gc.isInitialized(Constants.Y_MASK)) yoff = gc.getY();
				int toff = (int)gc.getR(); //T is stored in R field
				if(extruderOffset == null) extruderOffset = new Position[]{null,null,null,null,null}; //init for 5 extr.
				if(toff <= 4){
					extruderOffset[toff] = new Position(xoff,yoff);
				}
			}else if(gc.getGcode() == Constants.GCDEF.M108){
				if(gc.isInitialized(Constants.E_MASK)) m108=gc.getE();
			}else if(gc.isInitialized(Constants.SF_MASK)){//update Fan if specified
				currLayer.setFanspeed((int)gc.getFanspeed());
			}else if(gc.isInitialized(Constants.SE_MASK)){		//update Temperature if specified
				extemp=gc.getExtemp();			
			}else if(gc.isInitialized(Constants.SB_MASK)){ //Update Bed Temperature if specified
				bedtemp=gc.getBedtemp();
			} 
			
			currpos.updatePos(xpos,ypos); //reuse currposs obj, gcode just copies the floats
			gc.setCurrentPosition(currpos);
			//Add Gcode to Layer
			currLayer.addGcodes(gc,ig);
		}
		//System.out.println("Summarize Layers");
		for (Layer closelayer : layer) {
			endLayer(closelayer);	//finish old layer
		}
		//End last layer
		//endLayer(currLayer);
		
	}

	void endLayer(Layer lay) {
	//	time += lay.getTime();
		timeaccel += lay.getTimeAccel();
		distance += lay.getDistance();
		traveldistance += lay.getTraveldistance();


		
		
		// Count layers which are visited only
		if (!lay.isPrinted()) {
			notprintedLayers++;
		//	lay.setLayerheight(0); // Layer not printed
		} else {
			// calculate dimensions
			boundaries[0] = Math.max(lay.getBoundaries()[0], boundaries[0]);
			boundaries[1] = Math.min(lay.getBoundaries()[1], boundaries[1]);
			boundaries[2] = Math.max(lay.getBoundaries()[2], boundaries[2]);
			boundaries[3] = Math.min(lay.getBoundaries()[3], boundaries[3]);
			boundaries[4] = Math.max(lay.getBoundaries()[4], boundaries[4]);
			dimension[0] = Constants.round2digits(boundaries[0] - boundaries[1]);
			dimension[1] = Constants.round2digits(boundaries[2] - boundaries[3]);
			dimension[2] = Constants.round2digits(boundaries[4]);
			
			
			extrusion=extrusion+lay.getExtrusion();
			
			avgLayerHeight=avgLayerHeight +lay.getLayerheight(); //first printed layer
			avgbedtemp= avgbedtemp + lay.getBedtemp();
			avgextemp= avgextemp + lay.getExttemp();
//			System.out.println("Layer:"+lay.getNumber()+"Bedtemp:"+lay.getBedtemp()+" AVG:"+avgbedtemp);
		}

		//Summarize Speed values
		float sp = lay.getSpeed(Speed.SPEED_ALL_AVG);
		if(sp != Float.NaN && sp > 0){
			//average speed is relative to distance / divide by distance later
			avgspeed += (sp*lay.getDistance());
		}
		//Print/extrude only
		maxprintspeed=Math.max(maxprintspeed,lay.getSpeed(Speed.SPEED_PRINT_MAX));
		if(lay.getSpeed(Speed.SPEED_PRINT_MIN) != 0){
			minprintspeed=Math.min(minprintspeed,lay.getSpeed(Speed.SPEED_PRINT_MIN));
		}
		sp = lay.getSpeed(Speed.SPEED_TRAVEL_AVG);
		if(sp != Float.NaN && sp > 0){
			avgtravelspeed+= sp*lay.getTraveldistance();
		}
		
		// Update Speed Analysis for model ... combine layer data
		for (Iterator<Float> iterator = lay.getSpeedAnalysisT().keySet()
				.iterator(); iterator.hasNext();) {
			float speedkey = iterator.next();
			SpeedEntry timespeedlay = lay.getSpeedAnalysisT().get(speedkey);
			SpeedEntry speedsum = SpeedAnalysisT.get(speedkey);
			if (speedsum != null) {
				speedsum.addTime(timespeedlay.getTime());
				speedsum.addDistance(timespeedlay.getDistance());
				speedsum.setPrint(timespeedlay.getType());
				speedsum.addLayers(lay.getNumber());
			} else {
				SpeedEntry sped = new SpeedEntry(speedkey,timespeedlay.getTime(),lay.getNumber());
				sped.addDistance(timespeedlay.getDistance());
				sped.setPrint(timespeedlay.getType());
				SpeedAnalysisT.put(speedkey, sped);
			}
		}
		//Assume that unit is only set once
		unit=lay.getUnit();
	//	LayerOpen = false;
	}

	public float getAvgbedtemp() {
		return Constants.round2digits(avgbedtemp/getLayercount(true));
	}

	public float getAvgextemp() {
		return Constants.round2digits(avgextemp/getLayercount(true));
	}
	
	private Material guessMaterial(){
		String mat = System.getenv("FILAMENT_MATERIAL");
		if(mat != null){
			if("PLA".equals(mat)){
				return Material.PLA;
			}else{
				return Material.ABS;
			}
			
		}
		
		if(getAvgextemp() <= 210 && getAvgextemp() > 140){
			return Material.PLA;
		}
		if(getAvgextemp() < 290 && getAvgextemp() > 210){
			return Material.ABS;
		}
		return Material.PLA;
	}
	
	public String getFilamentReport(){
		String var 	= "";
		var 	   +="Material:"+getMaterial()+" "+getDiameter()+"mm\n";
		var		   +="Mass:   "+Constants.round2digits(getMass()/1000)+"cm3\n";
		var		   +="Weight: "+Constants.round2digits(getWeight())+"g\n";
		var		   +="Price:  "+Constants.round2digits(getPrice())+currency+"\n";
		return var;
	}
	
	public void setCurrency(String currency) {
		this.currency = currency;
	}
	/**
	 * Guess diameter 
	 * 1) user defined environment variable
	 * 2) comments in GCODE file
	 * 3) Fallback to some very rough calculation
	 * @return diameter
	 */
	private float guessDiameter(){
		//Read user defined environment variable if exists
		try {
			String dia = System.getenv("FILAMENT_DIAMETER");
			if(dia != null){
				//System.out.println("Use env value FILAMENT_DIAMETER="+dia);
				return Float.parseFloat(dia);
			}
		} catch (NumberFormatException e1) {
		}
		
		//get diameter from comments in gcode file
		try {
			GCodeStore codes = getGcodes();
			for (GCode gCode : codes) {
				//Ignore comments behind gcodes
				if (gCode.isComment()){
					//System.out.println("COMMENT"+gCode.getComment());
					if(gCode.getComment().matches(".*FILAMENT_DIAMETER\\s=.*")){ //SLICER
						//System.out.println("MATCHES:"+gCode.getComment());
						String[] res =gCode.getComment().split("=");
						return Float.parseFloat(res[1]);
					}else if(gCode.getComment().matches(".*FILAMENT_DIAMETER_.*")){ //SKEINFORGE
						//System.out.println("MATCHES:"+gCode.getComment());
						String[] res =gCode.getComment().split("[:<]");
						return Float.parseFloat(res[2]);					
					}
				}
			}
		} catch (Exception e) {
			//Comment parsing failed
		}
	//	System.err.println("Failed to parse GCODE comments for filament diameter. Fallback to guessing.");
		//Fallback ... no comments found
		isguessed=true;
		//Tried many formulars but there is no good way to guess the diameter (too many unknowns)
		//
		float exRadius=getAvgLayerHeight()/2; 
		float WOT = 2.1f;  //Assume a wide over thickness value of ~2.1 (heavily depends on nozzel size)
		double extrArea =  exRadius*(exRadius*WOT) * Math.PI; //Fläche extruded mm2
		double menge = extrArea*getDistance();
		double sizeArea = menge/getExtrusion();
		double guessedDia = Math.sqrt(sizeArea/Math.PI)*2;
	//	System.out.println("Extr menge mm3:"+menge/1000+" Estimate dia:"+guessedDia);
		
		//Either take 1.75 or 3mm
		if(guessedDia > 2.45f){
			return 3;
		}else if(guessedDia < 2.05f){
			return 1.75f;
		}
		//use 3
		System.out.println("Unable to detect diameter - Fallback to 3mm.\nPlease set environment variable FILAMENT_DIAMETER");
		return 3;
	}
	
	

	public float getAvgLayerHeight() {
		return Constants.round2digits(avgLayerHeight/(float)getLayercount(true));
	}
	
	/**
	 * Max and min positions on bed Offset 
	 * 	
	 * @return Array with 5 values
	 * xMax
	 * xMin
	 * yMax
	 * yMin
	 * zMax
	 * 
	 * 
	 */
	public float[] getBoundaries() {
		return boundaries;
	}
	
	/**
	 * get midsize x + y
	 * @return float[0] = X , float[1] = Y
	 */
	public float[] getmidpoint(){
		float[] midsz = new float[2];
		midsz[0] =  ((boundaries[0]-boundaries[1])/2)+boundaries[1];
		midsz[1] =  ((boundaries[2]-boundaries[3])/2)+boundaries[3];
		return midsz;
	}

	
	public float[] getDimension() {
		return dimension;
	}

	public float getDistance() {
		return Constants.round2digits(distance);
	}

	public float getExtrusion() {
		return Constants.round2digits(extrusion);
	}

	public String getFilename() {
		return filename;
	}
	
	public void setFilename(String fn) {
		filename=fn;
	}
	
	public void setInputstream(InputStream fn) {
		this.in=fn;
	}
	
	public String getUri(){
		if(uri != null) return uri;
		return filename;
	}
	
	public String getFilenameShort() {
		return new File(filename).getName();		
	}
	public int getGcodecount() {
		return gcodes.size();
	}

	public GCodeStore getGcodes() {
		if(gcodes==null) {
			gcodes = GCodeFactory.getGcodeStore(10000);
		}
		return gcodes;
	}
	public GCodeStore getGcodes(Layer lay) {
		GCodeStore gs = new GCodeStore();
		for (int i = lay.lowidx; i < lay.highidx; i++) {
			gs.add(gcodes.get(i));
		}
		return gs;
	}

	public ArrayList<Layer> getLayer() {
		return layer;
	}
	
	public Layer getLayer(int number) {
		
//		for (Layer lay : layer.values()) {
//			if(lay.getNumber()==number){
//				return lay;
//			}
//		}
		return layer.get(number);
	}

	public int getLayercount(boolean printedonly) {
		if (printedonly)
			return layercount - notprintedLayers;
		return layercount;
	}

	/**
	 * Get the average x/y move speed (ignoring gcodes with zero speed)
	 * Specify a speed type (All incl travel, printing/extruding moves only, travel moves only)
	 * @return speed in mm/s
	 */
	public float getSpeed(Speed type) {
		switch (type) {
		case SPEED_ALL_AVG:
			return Constants.round2digits(avgspeed/distance);
		case SPEED_TRAVEL_AVG:
			return Constants.round2digits((avgtravelspeed/traveldistance));
		case SPEED_PRINT_AVG:
			return Constants.round2digits((avgspeed-avgtravelspeed)/(distance-traveldistance));
		case SPEED_PRINT_MAX:
			return maxprintspeed;
		case SPEED_PRINT_MIN:
			return minprintspeed;
		default:
			return 0;
		}
		
	}

	public SortedMap<Float, SpeedEntry> getSpeedAnalysisT() {
		return SpeedAnalysisT;
	}


	public String getUnit() {
		return unit;
	}
	public boolean loadModel()throws IOException{
		FileInputStream fread =  new FileInputStream(filename);
		File f = new File(filename);
		filesize= f.length();
		System.out.println("Filesize:"+filesize);
		gcodes =  GCodeFactory.loadModel(fread,filesize);
		return  gcodes != null;
	}
	
	public boolean loadModel(InputStream in)throws IOException{
		gcodes =  GCodeFactory.loadModel(in,0);
		filesize=GCodeFactory.getReadBytes();
		return  gcodes != null;
	}
	
	public boolean loadModel(InputStream in, long size)throws IOException{
		filesize=size;
		gcodes =  GCodeFactory.loadModel(in,filesize);
		filesize=GCodeFactory.getReadBytes();
		return  gcodes != null;
	}
	
	public boolean isloaded(){
		return gcodes != null && getGcodecount() != 0;
	}
	
	

	public void saveModel(String newfilename)throws IOException{
		FileWriter fwr =  new FileWriter(newfilename);
		BufferedWriter gcwr= new BufferedWriter(fwr);
		
		for (GCode gc : gcodes) {
			gcwr.write(gc.getCodeline().toString());
			//gcwr.write("\n");
		}
		gcwr.close();
		fwr.close();
	}
	
	public String getModelComments(){
		StringBuilder buf = new StringBuilder(500);
		buf.append("--------- Slicer Comments------------\n");
		int max = 500; //max to avoid OOM
		for (GCode gCode : gcodes) {
			//Ignore comments behind gcodes
			if (max > 0 && gCode.isComment()){
				//System.out.println(gCode.getComment());
				buf.append(gCode.getComment());
				buf.append(Constants.newlinec);
				max--;
			}
			
		}
		return buf.toString();
	}
	public String getModelDetailReport(){
		float[] sizes = getDimension();
		float[] bound = getBoundaries();
		String mm_in = getUnit();
		StringBuilder varb = new StringBuilder(600);
		
		varb.append("Filename:  ");
		varb.append(getFilename());
		varb.append(Constants.newlinec);
		varb.append("Layers visited:  ");
		varb.append(getLayercount(false));
		varb.append(Constants.newlinec);
		varb.append("Layers printed:  ");
		varb.append(getLayercount(true));
		varb.append(Constants.newlinec);
		varb.append("Avg.Layerheight: ");
		varb.append(+getAvgLayerHeight());
		varb.append(mm_in);
		varb.append(Constants.newlinec);
		varb.append("Size:            ");
		varb.append(sizes[0]);
		varb.append(mm_in);
		varb.append(" x ");
		varb.append(sizes[1]);
		varb.append(mm_in);
		varb.append(" H");
		varb.append(sizes[2]);
		varb.append(mm_in);
		varb.append(Constants.newlinec);
		varb.append("Position on bed: ");
		varb.append(+Constants.round2digits(bound[1]));
		varb.append("/");
		varb.append(Constants.round2digits(bound[0]));
		varb.append(mm_in);
		varb.append(" x ");
		varb.append(Constants.round2digits(bound[3]));
		varb.append("/"+Constants.round2digits(bound[2]));
		varb.append(mm_in);
		varb.append(" H");
		varb.append(Constants.round2digits(bound[4]));
		varb.append(mm_in);
		varb.append(Constants.newlinec);
		
		varb.append("XY Distance:     ");
		varb.append(getDistance());
		varb.append(mm_in);
		varb.append(Constants.newlinec);
		
		varb.append("Extrusion:       ");
		varb.append(getExtrusion());
		varb.append(mm_in);
		varb.append(Constants.newlinec);
		
		varb.append("Number Extruders:");
		varb.append(getExtruderCount());
		varb.append(Constants.newlinec);
		
		varb.append("Bed Temperatur:  ");
		varb.append(getAvgbedtemp());
		varb.append("°\n");
		varb.append("Ext Temperatur:  ");
		varb.append(getAvgextemp());
		varb.append("°\n");
		varb.append("Avg.Speed(All):    ");
		varb.append(getSpeed(Speed.SPEED_ALL_AVG));
		varb.append(mm_in);
		varb.append("/s\n");
		varb.append("Avg.Speed(Print):  ");
		varb.append(getSpeed(Speed.SPEED_PRINT_AVG));
		varb.append(mm_in);
		varb.append("/s\n");
		varb.append("Avg.Speed(Travel): ");
		varb.append(getSpeed(Speed.SPEED_TRAVEL_AVG));
		varb.append(mm_in);
		varb.append("/s\n");
		varb.append("Max.Speed(Print):  ");
		varb.append(getSpeed(Speed.SPEED_PRINT_MAX));
		varb.append(mm_in);
		varb.append("/s\n");
		varb.append("Min.Speed(Print):  ");
		varb.append(getSpeed(Speed.SPEED_PRINT_MIN));
		varb.append(mm_in);
		varb.append("/s\n");
		varb.append("Gcode Lines:     ");
		varb.append(getGcodecount());
		varb.append(Constants.newlinec);
		varb.append("Overall Time (w/ Acceleration):    ");
		Constants.formatTimetoHHMMSS(timeaccel,varb);
		varb.append(" (");
		varb.append(timeaccel);
		varb.append("sec)\n");
		return varb.toString();
	}
	public String getModelSpeedReport(){
		StringBuilder var= new StringBuilder(); 
		var.append("---------- Model Speed Distribution ------------");
		ArrayList<Float> speeds = new ArrayList<Float>(getSpeedAnalysisT().keySet());
		for (Iterator<Float> iterator = speeds.iterator(); iterator.hasNext();) {
			float speedval =  iterator.next();
			SpeedEntry tim = getSpeedAnalysisT().get(speedval);
			var.append("\n  Speed ");
			var.append(speedval);
			var.append("    ");
			var.append(tim.getType());
			var.append("    Time:");
			var.append(Constants.round2digits(tim.getTime()));
			var.append("sec       ");
			var.append(Constants.round2digits(tim.getTime()/(timeaccel/100)));
			var.append('%');
			var.append("     Layers:[");	
			int max=4;
			//print the layer nr but only max of 4 (too much of info)
			for (Iterator<Integer> layrs = tim.getLayers().iterator(); layrs.hasNext();) {
				var.append(' ');
				var.append(layrs.next());
					max--;
					if(max==0){
						var.append(" ...");
						break;
					}
			}
			var.append(" ]");
		}

		return var.toString();
	}
	public String getModelLayerSummaryReport(){
		ArrayList<Layer> layers = new ArrayList<Layer>(getLayer());
		StringBuilder var= new StringBuilder();
		var.append("---------- Printed Layer Summary ------------\n");
		for (Iterator<Layer> iterator = layers.iterator(); iterator.hasNext();) {
			Layer lay = iterator.next();
			if(!lay.isPrinted()) continue;
			float layperc =  Constants.round2digits(lay.getTimeAccel()/(getTimeaccel()/100));
			var.append("  ");
			var.append(lay.getLayerSummaryReport());
			var.append("  ");
			var.append(layperc);
			var.append("%\n"); 
		}
		return var.toString();
	}
	Layer startLayer(float z, Layer prevLayer) {
		//if (LayerOpen)
		//	return null;
		
		//Z-Lift support
		if(prevLayer != null && z == prevLayer.getZPosition()) return prevLayer;
				
		//Layer lay = layer.get(z);
		Layer lay=null;
		int fanspeed=0;
		float bedtemp=0;
		float extemp=0;
		float lh = z;
		if (prevLayer != null ) {
			if(prevLayer.isPrinted()){
				lh = (z - prevLayer.getZPosition());
			}
			fanspeed=prevLayer.getFanspeed();
			bedtemp=prevLayer.getBedtemp();
			extemp=prevLayer.getExttemp();
		} 
		lay = new Layer(z, layercount,Constants.round2digits(lh));
		lay.setUnit(unit); //remember last unit
		lay.setFanspeed(fanspeed);
		lay.setBedtemp(bedtemp);
		lay.setExttemp(extemp);
		//layer.put(z, lay);
		layer.add(lay);
		layercount++;

		// System.out.println("Add Layer:"+z);
		//LayerOpen = true;
		return lay;
	}
	public float getTimeaccel() {
		return timeaccel;
	}

}
