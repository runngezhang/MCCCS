package workflow;

import ij.ImagePlus;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.graffiti.plugin.io.resources.FileSystemHandler;

import tools.ARFFProcessor;
import tools.IO_MCCCS.ReadMode;
import de.ipk.ag_ba.image.structures.Image;

/**
 * Converts image into an ARFF file.
 * 
 * @param class-count
 * @param input
 *           filename
 * @return ARFF file
 * @author Jean-Michel Pape, Christian Klukas
 */
public class ArffFromImageFileGenerator {
	
	public static void main(String[] args) throws Exception {
		{
			new Settings(true);
		}
		if (args == null || args.length < 1) { // [channel-count],
			System.err.println("No parameter for [class-count] and / or no [filenames] provided as parameters! Return Code 1");
			System.exit(1);
		} else {
			int parmIndex = 0;
			for (String a : args) {
				
				if (parmIndex == 0) {
					Settings.numberOfClasses = Math.abs(Integer.parseInt(a));
					parmIndex++;
					continue;
				}
				
				LinkedList<File> fl = new LinkedList<>();
				
				if (a.contains("*")) {
					String path = new File(a).getParent();
					for (File f : new File(path).listFiles((fn) -> {
						return fn.getName().startsWith(new File(a).getName().substring(0, new File(a).getName().indexOf("*")));
					})) {
						fl.add(f);
					}
				} else {
					fl.add(new File(a));
				}
				
				ARFFProcessor ac = new ARFFProcessor();
				
				for (File f : fl) {
					String name;
					// remove file-ending if it is directory
					if (!f.isDirectory()) {
						String name2 = f.getName();
						String[] split = name2.split("\\.");
						name = split[0];
					} else
						name = f.getName();
					
					String filename = ReadMode.IMAGES.getMode();
					String extension = ".tif";
					String[] flist = f.list();
					
					// read mask
					File file = new File(f.getParent() + "/mask_0.png");
					Image mask = null;
					if (file.exists())
						mask = new Image(FileSystemHandler.getURL(file));
					
					// search for all files channel*.tif
					TreeMap<String, String> hmap = new TreeMap<String, String>();
					for (String s : flist) {
						if (!s.endsWith(extension))
							continue;
						if (!s.startsWith(filename))
							continue;
						
						hmap.put(s.substring(0, s.lastIndexOf(".")), s);
					}
					
					ArrayList<String> arffFiles = new ArrayList<>();
					
					// read images and create arff file
					for (Entry<String, String> pair : hmap.entrySet()) {
						String channel_name = pair.getKey();
						try {
							String image_name = hmap.get(channel_name);
							ImagePlus ip = new ImagePlus(f.getAbsolutePath() + File.separator + image_name);;
							
							String pathOrURLToARFF = f.getAbsolutePath() + File.separator + channel_name;
							
							ac.convertImagesToArffNG(ip, pathOrURLToARFF, channel_name, name, mask, false);
							arffFiles.add(pathOrURLToARFF + ".arff");
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					}
					
					arffFiles.add(0, f.getAbsolutePath() + File.separator + name + "_" + Settings.numberOfClasses + ".arff");
					arffFiles.add("+?");
					MergeArffFiles.main(arffFiles.toArray(new String[] {}));
				}
			}
		}
	}
	
}
