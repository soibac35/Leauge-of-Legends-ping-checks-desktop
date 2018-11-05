import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

/*********************************************************
**												   		**
** 		Simple program to check ping on LOL server 		**
**		  										   		**
**********************************************************/
public class Main {
	
	// create thread
	private static ExecutorService threadService = Executors.newFixedThreadPool(2);
	private static ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
	
	private static boolean notified = false;
	
	// Settings
	private static long averagePing;
	private static int tolerance;
	private static String server1;
	private static String server2;
	private static long updateInterval;
	private static boolean notifications;
	private static boolean logToFile;
	
	// Window
	private static JFrame frame = new JFrame();
	private static PopupMenu popup;
	private static SystemTray tray;
	private static TrayIcon trayIcon;
	
	public static void main(String[] args) {
		loadSettings();
		setupTray();
		initiate();
	}
	private static void loadSettings() {
		try {
			Path path = filePrep(System.getProperty("user.dir") + "/settings.txt",false);
			if(!Files.exists(path)) {
				path = filePrep(System.getProperty("user.home") + "/settings.txt",false);
			}
			Properties prop = new Properties();
			InputStream input = new FileInputStream(path.toString());
			prop.load(input);
			averagePing = Long.parseLong(prop.getProperty("averagePing"));
			tolerance = Integer.parseInt(prop.getProperty("tolerance"));
			server1 = prop.getProperty("server1");
			server2 = prop.getProperty("server2");
			updateInterval = Long.parseLong(prop.getProperty("updateInterval"));
			notifications = Boolean.valueOf(prop.getProperty("notifications"));
			logToFile = Boolean.valueOf(prop.getProperty("logToFile"));
			OutputStream output = new FileOutputStream(path.toString());
			prop.store(output,null);
			input.close();
			output.close();
		} catch(Exception e) {e.printStackTrace();}
	}
	private static void initiate() {
		Runnable runnable = new Runnable() {
			public void run() {
				long ping = pingServer(server1);
				if(ping < 0 && !server2.equals("") && server2 != null) {
					ping = pingServer(server2);
				}
				notifyPing(ping);
			}
		};
		service.scheduleAtFixedRate(runnable,0L,updateInterval,TimeUnit.SECONDS);
	}
	private static long pingServer(String server) {
		try {
			long start = System.currentTimeMillis();
			InetAddress address = InetAddress.getByName(server);
			address.isReachable(10000);
			return System.currentTimeMillis()-start;
		} catch(Exception e) {e.printStackTrace();}
		return -1;
	}
	private static void notifyPing(long ping) {
		try {
			final long latency = ping;
			trayIcon.setImage(textToImage(ping));
			if(logToFile) {
				write(new SimpleDateFormat("MMM d, h:mma ").format(new Date()) + ping + "ms",System.getProperty("user.home") + "/log.txt");
			}
			if(notifications) {
				if(ping < 0 && !notified) {
					threadService.submit(new Runnable() {public void run() {JOptionPane.showMessageDialog(frame,"Error connecting to server","No Connection",JOptionPane.ERROR_MESSAGE);}});
					notified = true;
				}
				else if(ping >= averagePing+tolerance && !notified) {
					threadService.submit(new Runnable() {public void run() {JOptionPane.showMessageDialog(frame,"Ping is " + latency + "ms","High Ping",JOptionPane.ERROR_MESSAGE);}});
					notified = true;
				}
				else if(ping < averagePing+tolerance && notified) {
					threadService.submit(new Runnable() {public void run() {JOptionPane.showMessageDialog(frame,"Ping is " + latency + "ms");}});
					notified = false;
				}
			}
		} catch(Exception e) {e.printStackTrace();}
	}
	private static void setupTray() {
		try {
			// Window
			frame.setTitle("Ping Check");
			frame.setAlwaysOnTop(true);
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			
			// Tray Options
			popup = new PopupMenu();
			MenuItem defaultItem = new MenuItem("Exit");
			defaultItem.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					System.exit(0);
				}
			});
			popup.add(defaultItem);
			
			// Tray
			tray = SystemTray.getSystemTray();
			trayIcon = new TrayIcon(textToImage(0),"League Ping",popup);
			trayIcon.setImageAutoSize(true);
			frame.addWindowStateListener(new WindowStateListener() {
				public void windowStateChanged(WindowEvent e) {
					try {
						if(e.getNewState() == JFrame.ICONIFIED) {
							tray.add(trayIcon);
							frame.setVisible(false);
						}
						if(e.getNewState() == 7){
							tray.add(trayIcon);
							frame.setVisible(false);
						}
						if(e.getNewState() == JFrame.MAXIMIZED_BOTH) {
							tray.remove(trayIcon);
							frame.setVisible(true);
						}
						if(e.getNewState() == JFrame.NORMAL) {
							tray.remove(trayIcon);
							frame.setVisible(true);
						}
					} catch(Exception ex) {ex.printStackTrace();}
				}
			});
			
			tray.add(trayIcon);
		} catch(Exception e) {e.printStackTrace();}
	}
	public static Image textToImage(long number) {
		String text = Long.toString(number);
		Font font = new Font(Font.DIALOG,Font.PLAIN,18);
		BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = img.createGraphics();
		g2d.setFont(font);
		FontMetrics fm = g2d.getFontMetrics();
		int width = fm.stringWidth(text);
		int height = fm.getHeight();
		g2d.dispose();
		img = new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
		g2d = img.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
		g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g2d.setFont(font);
		fm = g2d.getFontMetrics();
		Color color = Color.YELLOW;
		if(number <= averagePing-5) {
			color = Color.GREEN;
		}
		else if(number >= averagePing+5) {
			color = Color.ORANGE;
		}
		g2d.setColor(color);
		g2d.drawString(text, 0, fm.getAscent());
		g2d.dispose();
		int trayIconWidth = new TrayIcon(img).getSize().width;
		return img.getScaledInstance(trayIconWidth,-1,Image.SCALE_SMOOTH);
	}
	private static void write(String line, String dir) {
		try {
			FileWriter writer = new FileWriter(filePrep(dir).toString(),true);
			writer.write(line + "\r\n");
			writer.close();
		} catch(Exception e) {e.printStackTrace();}
	}
	public static Path filePrep(String dir, boolean... createFile) {
		dir = dir.replaceAll("/","\\" + System.getProperty("file.separator"));
		Path path = Paths.get(dir);
		try {
			if((createFile.length == 0 || (createFile.length > 0 && createFile[0]))) {
				if(path.getParent() != null) {
					Files.createDirectories(path.getParent());
				}
				if(!Files.exists(path) && path.toString().substring(path.toString().length()-4).contains(".")) {
					Files.createFile(path);
				}
			}
		} catch(Exception e) {e.printStackTrace();}
		return path;
	}
}