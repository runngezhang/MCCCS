package tools;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.TreeSet;

import org.Colors;
import org.GapList;
import org.StringManipulationTools;

import de.ipk.ag_ba.image.structures.Image;
import de.ipk.ag_ba.image.structures.ImageStack;
import ij.ImagePlus;
import ij.process.ImageConverter;
import support.ImageStackAsARFF;
import workflow.Settings;

/**
 * @author pape, klukas
 */
public class ARFFProcessor {
	
	static double scale = 64d; // in case of 14-bit => 64d else 1d;
	
	/**
	 * Creates ARFF file for classifier training.
	 * 
	 * @param inputImages
	 * @param background
	 * @param path
	 * @param samplesize
	 * @param filename
	 * @param id
	 * @param calcAdditionalColorFeatures
	 * @throws IOException
	 */
	public static void createTrainingDataSet(ImageStackAsARFF[] inputImages,
			float backgroundValue, File path, int samplesize, String filename,
			boolean debug, String id)
			throws IOException {
		
		int numberOfClasses = inputImages.length;
		
		int width = inputImages[0].getWidth(); // cubes.get(0).length;
		int height = inputImages[0].getHeight(); // cubes.get(0)[0].length;
		int bands = inputImages[0].getBands(); // cubes.get(0)[0][0].length;
		
		int numberOfChannels = bands;
		
		// create header
		String attributes = "";
		for (int i = 0; i < numberOfChannels; i++) {
			String[] label = makeNice(inputImages[0].getImageLabel(i + 1)).split("#");
			attributes += "@attribute " + (label.length > 0 ? label[0] + "\tNUMERIC" : "") + System.lineSeparator();
		}
		
		File pathp = new File(path.getPath());
		if (!pathp.exists())
			pathp.mkdirs();
		
		try (PrintWriter sb = new PrintWriter(new BufferedWriter(new FileWriter(pathp + File.separator + filename + ".arff")))) {
			sb.write("%" + System.lineSeparator() + "@relation " + id + System.lineSeparator() + attributes + "@attribute class\t{");
			for (int idx = 0; idx < numberOfClasses; idx++) {
				if (idx < numberOfClasses - 1)
					sb.write("class" + idx + ",");
				else
					sb.write("class" + idx);
			}
			sb.write("}" + System.lineSeparator() + "@data" + System.lineSeparator());
			
			LinkedList<GapList<Integer>> sampleLists = new LinkedList<GapList<Integer>>();
			
			for (int classIndex = 0; classIndex < numberOfClasses; classIndex++) {
				GapList<Integer> sampleList = new GapList<>();
				inputImages[classIndex].lookForValidSamples(sampleList, backgroundValue);
				sampleLists.add(sampleList);
			}
			
			ImageStack debugSampleImagesStack = new ImageStack(numberOfClasses);
			int count = 0;
			
			// choose random samples
			for (int classIndex = 0; classIndex < sampleLists.size(); classIndex++) {
				GapList<Integer> sampleList = sampleLists.get(classIndex);
				int[][] debugSampleImage = new int[width][height];
				TreeSet<Integer> selectedSamples = new TreeSet<>();
				while ((count < samplesize || samplesize < 0) && sampleList.size() > 0) {
					int randidx = (int) (Math.random() * sampleList.size());
					selectedSamples.add(sampleList.get(randidx));
					sampleList.remove(randidx);
					count++;
				}
				
				for (String sampleString : getSampleStrings(selectedSamples, inputImages[classIndex])) {
					String[] st = sampleString.split(";");
					sb.write(st[0] + ",class" + classIndex + System.lineSeparator());
					// mark in debug image
					if (debug) {
						String[] coords = st[1].split(",");
						int xCoord = Integer.parseInt(StringManipulationTools.getNumbersFromString(coords[0]));
						int yCoord = Integer.parseInt(StringManipulationTools.getNumbersFromString(coords[1]));
						debugSampleImage[new Integer(xCoord)][new Integer(yCoord)] = Color.RED.getRGB();
					}
				}
				
				if (debug)
					debugSampleImagesStack.addImage("Class " + classIndex, new Image(debugSampleImage));
				
				count = 0;
			}
			
			if (debug)
				debugSampleImagesStack.show("debug sample images");
			
			sb.write("%");
		}
	}
	
	private static LinkedList<String> getSampleStrings(TreeSet<Integer> lines, ImageStackAsARFF inputImage) throws IOException {
		LinkedList<String> result = new LinkedList<>();
		inputImage.prepareGetIntensityReading();
		for (int lineIndex : lines) {
			StringBuilder line = new StringBuilder();
			line.append(inputImage.getIntensityValue(lineIndex));
			int y = lineIndex % inputImage.getHeight();
			int x = lineIndex / inputImage.getHeight();
			result.add(line.toString() + "; x: " + x + ", y: " + y);
		}
		inputImage.finalizeGetIntensityReading();
		return result;
	}
	
	/**
	 * Creates ARFF from given image stack.
	 * 
	 * @param isl
	 * @param path
	 * @param name
	 * @param debug
	 * @throws IOException
	 */
	public String convertImagesToArff(ImageStack isl, String path, String name, boolean debug)
			throws IOException {
		return convertImagesToArff(isl, path, name, null, debug);
	}
	
	/**
	 * Creates ARFF from given image stack, extract only pixels which are
	 * labeled as FG in mask image.
	 * 
	 * @param isl
	 * @param path
	 * @param name
	 * @param mask
	 * @param debug
	 * @return
	 * @throws IOException
	 */
	public String convertImagesToArff(ImageStack isl, String path, String name,
			Image mask_img,
			boolean debug) throws IOException {
		
		boolean checkMask = mask_img == null ? false : true;
		
		int[] mask = null;
		int background = 0;
		if (checkMask) {
			mask = mask_img.getAs1A();
			background = Settings.back;
		}
		
		int numberOfChannels = isl.size();
		
		String attributes = "";
		
		for (int i = 0; i < numberOfChannels; i++) {
			attributes += "@attribute " + makeNice(isl.getImageLabel(i + 1))
					+ "\tNUMERIC" + System.lineSeparator();
		}
		
		int numberOfDiseaseClasses = Settings.numberOfClasses;
		
		attributes += "@attribute class\t{";
		for (int idx = 0; idx < numberOfDiseaseClasses; idx++) {
			if (idx < numberOfDiseaseClasses - 1)
				attributes += ("class" + idx + ",");
			else
				attributes += ("class" + idx);
		}
		attributes += "}" + System.lineSeparator();
		
		String header = "%" + System.lineSeparator() + "@relation '" + name + "'" + System.lineSeparator() + attributes
				+ "@data" + System.lineSeparator();
		
		float[][][] cubeSliceXY = getFloatCubeSliceXY(isl, true);
		isl = null;
		
		int width = cubeSliceXY[0].length;
		int height = cubeSliceXY[0][0].length;
		int bands = cubeSliceXY.length;
		
		String line = "";
		String fileName = path + "/" + name + ".arff";
		
		try (FileWriter fw = new FileWriter(new File(fileName), false)) {
			fw.write(header);
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					if (checkMask)
						if (mask[x + y * width] == background)
							continue;
						
					for (int b = 0; b < bands; b++) {
						line += cubeSliceXY[b][x][y] + ",";
						
						if (line.length() > 0) {
							// appends the string to the file
							fw.write(line + "?" + System.lineSeparator());
							// .add(line + "; x: " + x + ", y: " + y);
							line = "";
						}
					}
				}
				fw.write("%");
			}
			return fileName;
		}
	}
	
	public static String makeNice(String imageLabel) {
		imageLabel = StringManipulationTools.stringReplace(imageLabel, "channel_", "");
		imageLabel = StringManipulationTools.stringReplace(imageLabel, "_Custom", "");
		imageLabel = StringManipulationTools.stringReplace(imageLabel, ".", "_");
		return imageLabel;
	}
	
	/**
	 * @return Slice/X/Y
	 */
	public float[][][] getFloatCubeSliceXY(ImageStack is,
			boolean removeExtractedSlicesToSaveMemory) {
		int bands = is.getStack().getSize();
		float[][][] cube = new float[bands][][];
		
		for (int b = 0; b < bands; b++) {
			float[][] slice = is.getProcessor(removeExtractedSlicesToSaveMemory ? 0 : b).getFloatArray();
			if (removeExtractedSlicesToSaveMemory)
				is.getStack().deleteSlice(1);
			cube[b] = slice;
		}
		
		return cube;
	}
	
	/**
	 * Default: Arff file has same size as foreground => useArffClassInformation
	 * = false, useAll = false FGBG conversion (only 2 classes, classifies whole
	 * image): => useArffClassInformation = true, useAll =false arff includes
	 * all pixels (more than foreground mask) => useAll = true
	 */
	public void convertArffToImage(String parent, String name, Image mask_img,
			boolean useArffClassInformation, boolean useAll, boolean debug)
			throws IOException {
		convertArffToImage(false, parent, name, mask_img, useArffClassInformation, useAll, debug);
	}
	
	/**
	 * Default: Arff file has same size as foreground => useArffClassInformation
	 * = false, useAll = false; FGBG conversion (only 2 classes, classifies
	 * whole image) => useArffClassInformation = true, useAll =false; arff
	 * includes all pixels (more than foreground mask) => useAll = true
	 */
	public void convertArffToGrayScaleImage(String parent, String name,
			Image mask_img, boolean useArffClassInformation, boolean useAll, boolean debug) throws IOException {
		convertArffToImage(true, parent, name, mask_img, useArffClassInformation, useAll, debug);
	}
	
	public void convertArffToImage(boolean grayScaleFromProbability,
			String parent, String name, Image mask_img,
			boolean useArffClassInformation, boolean useAll, boolean debug)
			throws IOException {
		
		ImagePlus ip = mask_img.getAsImagePlus();
		ImageConverter ic = new ImageConverter(ip);
		ic.convertToRGB();
		Image iii = new Image(ip);
		
		int[][] mask = iii.getAs2A();
		float[][] maskF = null;
		if (Settings.numberOfClasses == 1)
			maskF = iii.getAs2Afloat();
		
		String fn = parent + "/" + name + ".arff";
		File f = new File(fn);
		if (!f.exists()) {
			System.err.println("Error: File '" + fn + "' does not exist! Return 1");
			System.exit(1);
		}
		if (!f.canRead()) {
			System.err.println("Error: Can't read file '" + fn + "'! Return 2");
			System.exit(2);
		}
		
		try (
				FileReader fr = new FileReader(f);
				BufferedReader br = new BufferedReader(fr)) {
			// skip header
			boolean headpresent = true;
			boolean foundAttributeClassification = false;
			int offsetClassColumn = 0;
			
			while (headpresent) {
				String line = br.readLine();
				if (line.startsWith("@attribute classification")) {
					foundAttributeClassification = true;
				} else {
					if (foundAttributeClassification
							&& line.startsWith("@attribute")) {
						offsetClassColumn++; // found an additional column after the
						// classification attribute
					}
				}
				if (line.contains("@data"))
					headpresent = false;
			}
			
			ArrayList<Color> colorsAL = Colors.get(Settings.numberOfClasses, 1);
			int[] colors = new int[colorsAL.size()];
			
			int idx = 0;
			for (Color c : colorsAL)
				colors[idx++] = c.getRGB();
			
			boolean goToStart = false;
			int savedX = 0;
			int savedY = 0;
			
			out: for (int x = 0; x < mask_img.getWidth(); x++) {
				for (int y = 0; y < mask_img.getHeight(); y++) {
					if (goToStart) {
						x = savedX;
						y = savedY;
						goToStart = false;
					}
					if ((mask[x][y] != Settings.back || useArffClassInformation)
							|| useAll) {
						String line = br.readLine();
						// end of file ?
						if (line == null)
							break out;
						if (line.contains("%"))
							break out;
						if (line.length() == 0) {
							// skip empty lines in arff file
							goToStart = true;
							savedX = x;
							savedY = y;
							continue;
						}
						
						String[] s = line.split(",");
						if (useArffClassInformation) {
							if (grayScaleFromProbability) {
								// 2 classes => FGBG segmentation
								// 1 classes => simple grayscale image from prediction column
								if (Settings.numberOfClasses == 2 || Settings.numberOfClasses == 1) {
									String probabilityForClass0 = s[s.length - Settings.numberOfClasses];
									float p = Float.parseFloat(probabilityForClass0);
									if (maskF != null)
										maskF[x][y] = p;
									else
										mask[x][y] = new Color(p, p, p).getRGB();
								} else {
									System.err.println("Only FGBG supported!");
								}
							} else {
								String cl = s[s.length - 1 - offsetClassColumn];
								if ("class0".equals(cl)) {
									mask[x][y] = Settings.foreground;
								} else
									mask[x][y] = Settings.back;
							}
							
						} else {
							if (grayScaleFromProbability)
								throw new RuntimeException(
										"Output of grayscale image from probabilities of multiple classes is supported by ArffToProbabilityImageFileGenerator!");
							// last element defines class [0 ... n], in case of arff with added probabilities search for classX
							int classPosIdx = 0;
							for (String val : s) {
								if (val.contains("class")) {
									break;
								} else
									// check for overflow (in case of class string missing, but information is at last position)
									if (classPosIdx < (s.length - 1))
										classPosIdx++;
							}
							if (s.length > 0 && mask[x][y] != Settings.back) {
								int cls = Integer.parseInt(StringManipulationTools
										.getNumbersFromString(s[classPosIdx]));
								mask[x][y] = colors[cls];
							} else
								mask[x][y] = Settings.back;
						}
					}
				}
			}
		}
		
		// new Image(mask).show("mask applyclass0ToImage");
		if (maskF != null)
			new Image(maskF).saveToFile(parent + "/" + name + ".result.tiff");
		else
			if (useArffClassInformation)
				new Image(mask).saveToFile(parent + "/foreground.png");
			else
				new Image(mask).saveToFile(parent + "/classified.png");
	}
	
	public void convertArffToImageMultiLabel(String parent, String name,
			Image mask_img, boolean useAll, boolean debug) throws IOException {
		convertArffToImageMultiLabel(parent, name, mask_img, useAll, 0.0f, debug);
	}
	
	/**
	 * Creates grayscale image by using class probabilities (useAll = true =>
	 * use all information of arff file, ignores mask)
	 */
	public void convertArffToImageMultiLabel(String parent, String name,
			Image mask_img, boolean useAll, float acceptThreshold, boolean debug) throws IOException {
		
		ImagePlus ip = mask_img.getAsImagePlus();
		ImageConverter ic = new ImageConverter(ip);
		ic.convertToRGB();
		Image iii = new Image(ip);
		
		int[][] mask = iii.getAs2A();
		int[][] combined = iii.copy().getAs2A();
		
		int idxClass = 0;
		boolean combinedReady = false;
		
		int[] colors = new int[Settings.numberOfClasses];
		ArrayList<Color> colorsAL = new ArrayList<Color>();
		
		// in case of 2 classes use FGBG colors
		if (Settings.numberOfClasses > 2) {
			colorsAL = Colors.get(Settings.numberOfClasses, 1);
		} else {
			colorsAL.add(new Color(Settings.foreground));
			colorsAL.add(new Color(Settings.back));
		}
		
		int idx = 0;
		for (Color c : colorsAL)
			colors[idx++] = c.getRGB();
		
		// create grayscale image for each class
		for (idxClass = 0; idxClass < Settings.numberOfClasses; idxClass++) {
			try (
					FileReader fr = new FileReader(parent + "/" + name + ".arff");
					BufferedReader br = new BufferedReader(fr)) {
				// skip header
				boolean headpresent = true;
				while (headpresent) {
					String line = br.readLine();
					// System.out.println(line);
					if (line.contains("@data"))
						headpresent = false;
				}
				
				boolean goToStart = false;
				int savedX = 0;
				int savedY = 0;
				
				out: for (int x = 0; x < mask_img.getWidth(); x++) {
					for (int y = 0; y < mask_img.getHeight(); y++) {
						
						if (goToStart) {
							x = savedX;
							y = savedY;
							goToStart = false;
						}
						if (mask[x][y] != Settings.back || useAll) {
							String line = br.readLine();
							// end of file ?
							if (line == null)
								break out;
							if (line.contains("%"))
								break out;
							if (line.length() == 0) {
								// skip empty lines in arff file
								goToStart = true;
								savedX = x;
								savedY = y;
								continue;
							}
							String[] s = line.split(",");
							boolean classInfoReached = false;
							int count = 0;
							float[] probabilities = new float[Settings.numberOfClasses];
							for (String v : s) {
								if (v.contains("class")) {
									classInfoReached = true;
									continue;
								}
								if (classInfoReached) {
									probabilities[count] = Float.parseFloat(v);
									count++;
								}
							}
							// convert probability to grayValue
							if (s.length > 0
									&& (useAll || mask[x][y] != Settings.back)) {
								int val = (int) (probabilities[idxClass] * 255);
								
								try {
									mask[x][y] = new Color(val, val, val).getRGB();
								} catch (Exception e) {
									System.out.println("Invalid probability value (can't interpret as color value, range 0..255): " + val);
								}
								if (!combinedReady) {
									float highestProbability = -1f;
									// int colorForResult = combined[x][y];
									int colorForResult = Color.BLACK.getRGB();
									int probIdx = 0;
									for (float prob : probabilities) {
										if (prob > acceptThreshold && prob > highestProbability) {
											highestProbability = prob;
											colorForResult = colors[probIdx];
										}
										probIdx++;
									}
									combined[x][y] = colorForResult;
								}
							} else {
								mask[x][y] = Settings.back;
								if (!combinedReady)
									combined[x][y] = Settings.back;
							}
						} else {
							String line = br.readLine();
							// end of file ?
							if (line == null)
								break out;
						}
					}
				}
			}
			
			new Image(mask).saveToFile(parent + "/probability_" + idxClass
					+ ".png");
			
			if (!combinedReady) {
				new Image(combined).saveToFile(parent + "/probability_combined"
						+ ".png");
				combinedReady = true;
			}
		}
		
	}
	
	/**
	 * Creates grayscale image by using class probabilities (uses all
	 * information of arff file, ignores mask)
	 */
	public void convertArffToImageMultiLabelFloatImage(String parent, int w,
			int h, String name, boolean debug) throws IOException {
		// create grayscale image for each class
		for (int idxClass = 0; idxClass < Settings.numberOfClasses; idxClass++) {
			try (
					FileReader fr = new FileReader(parent + "/" + name + ".arff");
					BufferedReader br = new BufferedReader(fr)) {
				float[][] mask = new float[w][h];
				// skip header
				boolean headpresent = true;
				while (headpresent) {
					String line = br.readLine();
					// System.out.println(line);
					if (line.contains("@data"))
						headpresent = false;
				}
				
				boolean goToStart = false;
				int savedX = 0;
				int savedY = 0;
				
				out: for (int x = 0; x < w; x++) {
					for (int y = 0; y < h; y++) {
						
						if (goToStart) {
							x = savedX;
							y = savedY;
							goToStart = false;
						}
						String line = br.readLine();
						// end of file ?
						if (line == null)
							break out;
						if (line.contains("%"))
							break out;
						if (line.length() == 0) {
							// skip empty lines in arff file
							goToStart = true;
							savedX = x;
							savedY = y;
							continue;
						}
						String[] s = line.split(",");
						boolean classInfoReached = false;
						int count = 0;
						float probability = 0;
						for (String v : s) {
							if (v.contains("class")) {
								classInfoReached = true;
								continue;
							}
							if (classInfoReached) {
								if (idxClass == count) {
									probability = Float.parseFloat(v);
									break;
								}
								count++;
							}
						}
						// convert probability to grayValue
						if (s.length > 0)
							mask[x][y] = probability;
					}
				}
				new Image(mask).saveToFile(parent + "/probability_" + idxClass + ".tif");
			}
		}
	}
	
	/**
	 * Creates grayscale image by using class probabilities (uses all
	 * information of arff file)
	 * 
	 * @author Christian Klukas
	 */
	public void convertArffToImageMultiLabelFloatImage2(String arffFileName, int w, int h) throws IOException {
		// create grayscale image for each class
		File arffFile = new File(arffFileName);
		String directory = arffFile.getParent();
		String fileNameWithoutExt = StringManipulationTools.removeFileExtension(arffFile.getName());
		try (FileReader fr = new FileReader(arffFileName); BufferedReader br = new BufferedReader(fr)) {
			// skip header
			boolean headpresent = true;
			while (headpresent) {
				String line = br.readLine();
				// System.out.println(line);
				if (line.contains("@data"))
					headpresent = false;
			}
			
			int classCnt = -1;
			boolean goToStart = false;
			int savedX = 0;
			int savedY = 0;
			
			float[][][] mask = null;
			float[][][] mask_bm = null;
			
			String[] classNames = null;
			
			int attributesBeforeClassInfo = 0;
			boolean foundDataAttribute = false;
			
			out: for (int x = 0; x < w; x++) {
				for (int y = 0; y < h; y++) {
					if (goToStart) {
						x = savedX;
						y = savedY;
						goToStart = false;
					}
					
					String line = br.readLine();
					// end of file ?
					if (line == null)
						break out;
					if (line.contains("%"))
						break out;
					if (line.length() == 0) {
						// skip empty lines in arff file
						goToStart = true;
						savedX = x;
						savedY = y;
						continue;
					}
					if (line.startsWith("@attribute class")) {
						line = line.substring(line.indexOf("{"));
						line = line.substring(0, line.indexOf("}"));
						classNames = line.split(",");
						classCnt = classNames.length;
						mask = new float[classCnt][w][h];
						mask_bm = new float[classCnt][w][h];
					} else {
						if (line.startsWith("@attribute") && classNames == null)
							attributesBeforeClassInfo = attributesBeforeClassInfo + 1;
					}
					
					if (line.startsWith("@data")) {
						foundDataAttribute = true;
					}
					
					if (foundDataAttribute) {
						String[] s = line.split(",");
						float maxProb = -Float.MAX_VALUE;
						int maxIdx = -1;
						for (int classIdx = 0; classIdx < classNames.length; classIdx++) {
							float prob = Float.parseFloat(s[attributesBeforeClassInfo + 1 + classIdx]);
							mask[classIdx][x][y] = prob;
							mask[classIdx][x][y] = prob;
							if (prob > maxProb) {
								maxProb = prob;
								maxIdx = classIdx;
							}
						}
						if (maxIdx >= 0) {
							mask_bm[maxIdx][x][y] = 1f;
						}
					}
				}
			}
			for (int classIdx = 0; classIdx < classNames.length; classIdx++) {
				new Image(mask[classIdx]).saveToFile(directory + "/" + fileNameWithoutExt + "_pr_" + classNames[classIdx] + ".tif");
			}
			
			for (int classIdx = 0; classIdx < classNames.length; classIdx++) {
				new Image(mask[classIdx]).saveToFile(directory + "/" + fileNameWithoutExt + "_bm_" + classNames[classIdx] + ".tif");
				new Image(mask_bm[classIdx]).saveToFile(directory + "/" + fileNameWithoutExt + "_bm_" + classNames[classIdx] + ".tif");
			}
		}
	}
	
	/**
	 * Reads a single channel image and creates Arff file.
	 * 
	 * @param ip
	 * @param path
	 * @param name
	 * @param mask_img
	 * @param b
	 * @return
	 * @throws IOException
	 */
	public String convertImagesToArffNG(ImagePlus ip, String out_path_name, String channel_name, String folder_name, Image mask_img, boolean b)
			throws IOException {
		boolean checkMask = mask_img == null ? false : true;
		
		int[] mask = null;
		int background = 0;
		if (checkMask) {
			mask = mask_img.getAs1A();
			background = Settings.back;
		}
		
		String attributes = "";
		
		for (int i = 0; i < 1; i++) {
			attributes += "@attribute " + makeNice(channel_name)
					+ "\tNUMERIC" + System.lineSeparator();
		}
		
		String header = "%" + System.lineSeparator() + "@relation '" + folder_name + "'" + System.lineSeparator() + attributes
				+ "@data" + System.lineSeparator();
		
		float[][] XY = ip.getProcessor().getFloatArray();
		
		int width = XY.length;
		int height = XY[0].length;
		
		String line = "";
		
		String fn = out_path_name + ".arff";
		
		try (FileWriter fw = new FileWriter(new File(fn), false)) {
			fw.write(header);
			
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					if (checkMask)
						if (mask[x + y * width] == background)
							continue;
						
					line += XY[x][y];
					
					if (line.length() > 0) {
						// appends the string to the file
						fw.write(line + System.lineSeparator());
						line = "";
					}
				}
			}
			fw.write("%");
		}
		return fn;
	}
}
