/*	VIDIS is a simulation and visualisation framework for distributed systems.
	Copyright (C) 2009 Dominik Psenner, Christoph Caks
	This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
	This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
	You should have received a copy of the GNU General Public License along with this program; if not, see <http://www.gnu.org/licenses/>. */
package vidis.ui.mvc;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCanvas;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLCapabilitiesChooser;
import javax.media.opengl.GLContext;
import javax.media.opengl.GLEventListener;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Point4d;
import javax.vecmath.Vector3d;

import org.apache.log4j.Logger;

import vidis.ui.config.Configuration;
import vidis.ui.events.CameraEvent;
import vidis.ui.events.IVidisEvent;
import vidis.ui.events.ObjectEvent;
import vidis.ui.events.VidisEvent;
import vidis.ui.events.mouse.AMouseEvent;
import vidis.ui.input.InputListener;
import vidis.ui.model.impl.Link;
import vidis.ui.model.impl.Node;
import vidis.ui.model.impl.NodeField;
import vidis.ui.model.impl.Packet;
import vidis.ui.model.impl.PacketField;
import vidis.ui.model.structure.ASimObject;
import vidis.ui.model.structure.IGuiContainer;
import vidis.ui.model.structure.IVisObject;
import vidis.ui.mvc.api.AController;
import vidis.ui.mvc.api.Dispatcher;
import vidis.ui.vis.Light;
import vidis.ui.vis.camera.FreeLookCamera;
import vidis.ui.vis.camera.GuiCamera;
import vidis.ui.vis.camera.ICamera;
import vidis.ui.vis.objects.Axis;
import vidis.ui.vis.objects.Grid;
import vidis.ui.vis.objects.Selector;
import vidis.ui.vis.shader.ShaderFactory;

import com.sun.opengl.util.Animator;

public class SceneController extends AController implements GLEventListener {

	private static Logger logger = Logger.getLogger( SceneController.class );
	private static Logger glLogger = Logger.getLogger( "vis.opengl" );
	
	private List<ICamera> cameras = new LinkedList<ICamera>();
	
	private Selector selector = new Selector();
	
	private List<IVisObject> objects = Collections.synchronizedList( new LinkedList<IVisObject>() );
	private List<IVisObject> objectsToDel = Collections.synchronizedList( new ArrayList<IVisObject>() );
	private List<IVisObject> objectsToAdd = Collections.synchronizedList( new ArrayList<IVisObject>() );
	
	
	private GLCanvas canvas;
	
	/**
	 * Used to animate the scene
	 */
	private Animator animator;
	
	private int wantedFps = 35;
	
	private long startTime;
	
	private int fps_log_max = 12;
	private List<Double> fps_log = new LinkedList<Double>();
	
	private NodeField nodeCapturingSource = null;
	private PacketField packetCapturingSource = null;
	
	private int warnLevel_laptopTooSlow;
	private int autoAdjustDetailLevelCounter;
	
	public SceneController() {
		logger.debug( "Constructor()" );
		addChildController( new CameraController() );
		addChildController( new GuiController() );
		
		registerEvent( IVidisEvent.InitScene );
		
		registerEvent( IVidisEvent.CameraRegister,
					   IVidisEvent.CameraUnregister );
		
		registerEvent( IVidisEvent.ObjectRegister, 
					   IVidisEvent.ObjectUnregister );
		
		registerEvent( IVidisEvent.MouseReleasedEvent_3D2,
				   IVidisEvent.MouseMovedEvent_3D2 );
		
		registerEvent( IVidisEvent.StartNodeCapturing,
					   IVidisEvent.StartPacketCapturing );
		
		registerEvent( IVidisEvent.SelectASimObject );
		
		registerEvent( IVidisEvent.AutoAdjustDetailLevel );
	}
	
	@Override
	public void handleEvent( IVidisEvent event ) {
		logger.debug( "handleEvent( "+event+" )" );
		switch ( event.getID() ) {
		case IVidisEvent.AutoAdjustDetailLevel:
			// reset detail level
			Configuration.DETAIL_LEVEL = 0.0;
			// then start adjusting
			autoAdjustDetailLevelCounter = Configuration.USE_AUTOMATIC_DETAIL_LEVEL_COUNTER;
			break;
		case IVidisEvent.InitScene:
			initialize();

			logger.info( "sending InitCamera Event" );
			Dispatcher.forwardEvent( IVidisEvent.InitCamera );
			
			logger.info( "sending InitGui Event" );
			Dispatcher.forwardEvent( IVidisEvent.InitGui );
			
			logger.info( "sending RegisterCanvas Event" );
			Dispatcher.forwardEvent( new VidisEvent<GLCanvas>( IVidisEvent.RegisterCanvas, canvas ) );
			
			break;
		case IVidisEvent.SelectASimObject:
			selector.setSelectedObject( ((VidisEvent<ASimObject>)event).getData() );
			break;
		case IVidisEvent.CameraRegister:
			registerCamera( ((CameraEvent)event).getCamera() );
			break;
		case IVidisEvent.CameraUnregister:
			unregisterCamera( ((CameraEvent)event).getCamera() );
			break;	
		case IVidisEvent.ObjectRegister:
			registerObject( ((ObjectEvent)event).getObject() );
			break;
		case IVidisEvent.ObjectUnregister:
			IVisObject o = ((ObjectEvent)event).getObject();
			if ( o.equals( selector.getSelectedObject() ) ) {
				selector.resetSelection();
			}
			unregisterObject( o );
			
			break;
		case IVidisEvent.MouseReleasedEvent_3D2:
		case IVidisEvent.MouseMovedEvent_3D2:
			handleMouseEvent( (AMouseEvent)event );
			break;
		case IVidisEvent.StartNodeCapturing:
			nodeCapturingSource = ((VidisEvent<NodeField>)event).getData();
			break;
		case IVidisEvent.StartPacketCapturing:
			packetCapturingSource = ((VidisEvent<PacketField>)event).getData();
			break;
		}	
		forwardEventToChilds( event );
	}

	private void initialize() {
		GraphicsDevice sd[] = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
		logger.info( "Graphics Devices: " );
		for ( GraphicsDevice d : sd ) {
			logger.info( " -<> "+ d );
			
		}
		GraphicsDevice graphicsDevice = sd[0];
		GraphicsConfiguration graphicsConfiguration = graphicsDevice.getDefaultConfiguration();
		GLCapabilities glCapabilities = new GLCapabilities();
		glCapabilities.setDoubleBuffered( true );
		glCapabilities.setHardwareAccelerated( true );
		
		GLCapabilitiesChooser chooser = new GLCapabilitiesChooser() {
			public int chooseCapabilities(GLCapabilities desired,
					GLCapabilities[] available,
					int windowSystemRecommendedChoice) {
				return 1;
			}
		};
		GLContext glContext = GLContext.getCurrent();
		
		
		canvas = new GLCanvas();
		
		canvas.addGLEventListener( this );
		
		InputListener l = new InputListener();
		canvas.addKeyListener(l);
		canvas.addMouseWheelListener(l);
		canvas.addMouseMotionListener(l);
		canvas.addMouseListener(l);
		
		// add some default objects
		
		// the axis was only for debugging purpose
//		registerObject( new Axis() );
		
		// for logo screenshot
		registerObject( new Grid() );

		
		// for logo screenshot
		registerObject( selector );
	}
	
	
	private void updateObjects() {
		synchronized ( objectsToAdd ) {
			if ( objectsToAdd.size() > 0 ) {
				synchronized (objects) {
					objects.addAll( objectsToAdd );
				}
				objectsToAdd.clear();
			}
		}
		synchronized ( objectsToDel ) {
			if ( objectsToDel.size() > 0 ) {
				synchronized ( objects ) {
					objects.removeAll( objectsToDel );
				}
				objectsToDel.clear();
			}
		}
	}
	
	
	
	/**
	 * display event
	 * draws the whole scene
	 */
	public void display(GLAutoDrawable drawable) {
		
		Dispatcher.forwardEvent( IVidisEvent.UpdateFromRenderLoop );
		
		// do thedateObjects(); update thing
		updateObjects();
		
		final GL gl = drawable.getGL();
		
		startTime = System.currentTimeMillis();
		
		//XXX update cameras somewhere else
		for ( ICamera c : cameras ) {
			c.update();
		}
		
//		for ( ICamera c : cameras ) {
		for (int i=0; i<cameras.size(); i++) {
			ICamera c = cameras.get(i);
			// INIT
			c.init(gl);
			
			// PROJECTION
			gl.glMatrixMode( GL.GL_PROJECTION );
			gl.glLoadIdentity();
			c.applyProjectionMatrix(gl);ShaderFactory.removeAllPrograms(gl);
			
			// VIEW
			gl.glMatrixMode( GL.GL_MODELVIEW );
			gl.glLoadIdentity();
			c.applyViewMatrix(gl);
			
			// MODEL
			drawModel( gl, c);
			
			// TEXT
//			if ( c instanceof FreeLookCamera ) {
//				draw3DText( gl );
//			}
			
		}
		
		long usedTime = System.currentTimeMillis() - startTime;
		
		double fps = 0;
		
		if(usedTime != 0) {
			fps = 1000d / usedTime;
			fps_log.add(fps);
			if(fps_log_max < fps_log.size())
				fps_log.remove(0);
		}
		
		double fpsMiddle = median(fps_log);
		if(Configuration.USE_AUTOMATIC_DETAIL_LEVEL && autoAdjustDetailLevelCounter > 0) {
			autoAdjustDetailLevelCounter--;
			// here we check if we have to decrease / increase the detail level
			if( fpsMiddle > 0 && fps_log.size() == fps_log_max ) {
				if ( ! inRange(fpsMiddle, wantedFps-2, wantedFps+2) ) {
					double factor = (fpsMiddle / wantedFps) - 1;
					double adjust = 0.005 * factor;
					Configuration.DETAIL_LEVEL = Math.max(0, Math.min(1.5, Configuration.DETAIL_LEVEL + adjust ));
					if ( Configuration.DETAIL_LEVEL == 0 ) {
						warnLevel_laptopTooSlow++;
						if(warnLevel_laptopTooSlow >= fps_log_max*2 && warnLevel_laptopTooSlow % (fps_log_max*2) == 0)
							logger.warn( " YOUR COMPUTER/LAPTOP IS TOO SLOW TO RUN THIS PROGRAM WITH MINIMUM DETAIL LEVEL, GET YOURSELF A NEW ONE!" );
					}
				}
			}
		}
		Dispatcher.forwardEvent( new VidisEvent<Double>( IVidisEvent.FPS, fpsMiddle ) );
	}
	
	private static boolean inRange (double value, double minV, double maxV) {
		return value > minV && value < maxV;
	}
	
	private double median(List<Double> list) {
		if(list.size() > 0) {
			double sum = 0;
			for(Double d : list) {
				sum += d;
			}
			return sum / list.size();
		}
		return 0;
	}

	private void draw3DText( GL gl ) {
		boolean depthTest = gl.glIsEnabled( GL.GL_DEPTH_TEST );
		gl.glDisable( GL.GL_DEPTH_TEST );
		gl.glPushMatrix();
		for ( IVisObject o : objects ) {
			if ( o.isTextRenderable() ) {
				o.renderText( gl );
			}
		}
		gl.glPopMatrix();
		if ( depthTest ) {
			gl.glEnable( GL.GL_DEPTH_TEST );
		}
	}
	
	private void drawModel( GL gl, ICamera c ) {
		if ( c instanceof GuiCamera) {
			
			gl.glPolygonMode( GL.GL_FRONT_AND_BACK, GL.GL_FILL );
			
			ShaderFactory.removeAllPrograms(gl);
			
			gl.glPushMatrix();
			for ( IVisObject o : objects ) {
				if ( o instanceof IGuiContainer ) {
					o.render(gl);
				}
			}
			gl.glPopMatrix();
		}
		else {
			if ( c instanceof FreeLookCamera ) {
				FreeLookCamera cam = (FreeLookCamera) c;
				for ( IVisObject o : objects ) {
					if ( (o instanceof Node) ) {
						if ( ((Node) o).getOnScreenLabel() != null ) {
							try {
								Point3d pkt = ((Node) o).getPosition();
								Point4d pkt2 = new Point4d( pkt.x, pkt.y, pkt.z, 0 );
								Point2d result = cam.calc2dfrom3d(pkt2, gl);
								((Node) o).getOnScreenLabel().setX( result.x );
								((Node) o).getOnScreenLabel().setY( result.y );
							} catch(NullPointerException e) {
								logger.error("Node "+o+" has no position.", e);
							}
						}
					}
				}
			}
			
			if ( Configuration.DISPLAY_WIREFRAME ) {
				gl.glPolygonMode( GL.GL_FRONT_AND_BACK, GL.GL_LINE );
			}
//			if ( P != null && A != null ) {
//				gl.glBegin( GL.GL_LINES );
//					gl.glVertex3d( A.x, A.y, A.z );
//					gl.glVertex3d( P.x, P.y, P.z );
//				gl.glEnd();
//			}
//			for ( RenderPass p : RenderPass.values()) {
//				p.setup(gl);
				// MODEL  with draw order:
				// first draw the nodes
			    // then the back side of the links
				// then the packets
				// and finally the front sides of the links
				gl.glPushMatrix();
//					gl.glEnable( GL.GL_LIGHTING );
					// nodes
					gl.glEnable( GL.GL_LIGHT1 );
					for ( IVisObject o : objects ) {
						if ( (o instanceof Node) ) {
							o.render(gl);
						}
					}
					// packets
					for ( IVisObject o : objects ) {
						if ( (o instanceof Packet) ) {
							o.render(gl);
						}
					}
					// links
					gl.glEnable( GL.GL_BLEND );
//					gl.glEnable( GL.GL_CULL_FACE );
//					gl.glCullFace( GL.GL_BACK );
					
//					gl.glBlendFunc( GL.GL_ONE, GL.GL_DST_ALPHA );
//					gl.glColor4d( 0, 0, 1, 0.7 );
					for ( IVisObject o : objects ) {
						if ( (o instanceof Link) ) {
							o.render(gl);
						}
					}
					gl.glDisable( GL.GL_BLEND );
					gl.glDisable( GL.GL_CULL_FACE );
					gl.glPolygonMode( GL.GL_FRONT_AND_BACK, GL.GL_FILL );
					
					// rest
					gl.glDisable( GL.GL_LIGHTING );
					for ( IVisObject o : objects ) {
						if ( !(o instanceof Node) && !(o instanceof Link) && !(o instanceof Packet) && !(o instanceof IGuiContainer) ) {
							o.render(gl);
						}
				}
				gl.glPopMatrix();
//			}
		}
	}
	
	
	
	/**
	 * displayChanged event
	 * called when displaymode has changed
	 */
	public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
		glLogger.debug( "displayChanged()" );
	}

	/**
	 * init method
	 * called when gl context is created
	 */
	public void init(GLAutoDrawable drawable) {
		glLogger.debug( "init()" );
		final GL gl = drawable.getGL();
		
		
		// enable / disable some global stuff
		ShaderFactory.init(gl);
		
		Light.initNodeLight(gl);
		Light.initLinkLight(gl);
		Light.initPacketLight(gl);
		
		glLogger.info("init shader prog");
		Link.setupShaderProgram(gl);
		glLogger.info("done with shader init");
		
		animator = new Animator(drawable);
		
		animator.start();
	}

	/**
	 * reshape event
	 */
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		glLogger.debug( "reshape()" );
		// update the camera targets
		for (ICamera c : cameras)
			c.reshape(x, y, width, height);
	}
	
	private void registerCamera( ICamera c ) {
		cameras.add(c);
	}
	private void unregisterCamera( ICamera c ) {
		cameras.remove(c);
	}

	private void registerObject( IVisObject o ) {
		synchronized ( objectsToAdd ) {
			objectsToAdd.add( o );
		}
	}
	
	private void unregisterObject( IVisObject o ) {
		if ( o == null ) logger.error ( " SHOULD I KILL A NULL OBJECT?? ");
		try {
			o.kill();
			synchronized ( objectsToDel ) {
				objectsToDel.add( o );
			}
		} catch ( NullPointerException e ) {
			e.printStackTrace();
		}
	}
	
	public synchronized List<IVisObject> getRegisteredObjects() {
		return new ArrayList<IVisObject>( objects );
	}
	
	private ASimObject underMouseObject = null;
	
	Point3d P;
	Point3d A;
	private void handleMouseEvent( AMouseEvent e ) {
		A = new Point3d( e.rayOrigin.x, e.rayOrigin.y, e.rayOrigin.z );
		Vector3d g = new Vector3d ( e.ray.x, e.ray.y, e.ray.z ) ;
		g.normalize();
		
		List<IVisObject> obj = getRegisteredObjects();
		Vector3d AP = new Vector3d();
		Vector3d dist = new Vector3d();
		double nearestDistance = Double.MAX_VALUE;
		ASimObject nearestObject = null;
		
		for ( IVisObject o : obj ) {
			if ( o instanceof ASimObject ) {
				try { 
					P = ((ASimObject)o).getPosition();
					
					AP.sub( P, A );
					
					dist.set( 
							g.y*AP.z - g.z*AP.y,
							g.z*AP.x - g.x*AP.z,
							g.x*AP.y - g.y*AP.x);
					double l = dist.length();
					if ( l < ((ASimObject)o).getHitRadius() ) {
						double way = AP.length();
						if ( way < nearestDistance ) {
							nearestDistance = way;
							nearestObject = (ASimObject) o;
						}
					}
				}
				catch ( Exception ex ) {
					logger.debug( "0815 error", ex );
				}
			}
			else {
				logger.debug( o + " was wrong" );
			}
		}
		if ( nearestObject != null ) {
			if ( e.getID() == IVidisEvent.MouseReleasedEvent_3D2 ) {
				if ( nearestObject instanceof Node ) {
					if ( nodeCapturingSource != null ) {
						nodeCapturingSource.setNode( (Node) nearestObject );
						nodeCapturingSource = null;
					}
					else {
						nearestObject.onClick();
					}
				}
				else if ( nearestObject instanceof Packet ) {
					if ( packetCapturingSource != null ) {
						packetCapturingSource.setPacket( (Packet) nearestObject );
						packetCapturingSource = null;
					}
					else {
						nearestObject.onClick();
					}
				}
				else {
					nearestObject.onClick();
				}
			}
		}
		
		if ( nearestObject != null && underMouseObject != null ) {
			if ( ! nearestObject.equals( underMouseObject ) ) {
				// mouse out underMouseObject
				logger.info( "onMouseOut " + underMouseObject );
				underMouseObject.onMouseOut();
				// mouse in nearestObject
				underMouseObject = nearestObject;
				logger.info( "onMouseIn " + underMouseObject );
				underMouseObject.onMouseIn();
				
			}
		}
		else if ( nearestObject != null && underMouseObject == null ){
			underMouseObject = nearestObject;
			underMouseObject.onMouseIn();
			logger.info( "onMouseIn " + underMouseObject );
		}
		else if ( nearestObject == null && underMouseObject != null ) {
			logger.info( "onMouseOut " + underMouseObject );
			underMouseObject.onMouseOut();
			underMouseObject = null;
		}
	}
}
