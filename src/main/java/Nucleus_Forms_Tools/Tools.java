package Nucleus_Forms_Tools;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.filter.ParticleAnalyzer;
import java.awt.Color;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import javax.swing.ImageIcon;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.plugins.util.ImageProcessorReader;
import org.apache.commons.io.FilenameUtils;


/**
 * @author phm
 */
public class Tools {
    public final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));
    
    public Calibration cal = new Calibration();
    private double pixSurf;
    
    private double minNucSurf= 50;
    private double maxNucSurf = Double.MAX_VALUE;   
    public double sigma = 7;
    
        
    /**
     * Find images in folder
     */
    public ArrayList findImages(String imagesFolder, String imageExt) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        if (files == null) {
            System.out.println("No image found in " + imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            // Find images with extension
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExt) && !f.startsWith("."))
                images.add(imagesFolder + File.separator + f);
        }
        Collections.sort(images);
        return(images);
    }
    
    
    /**
     * Find image calibration
     */
    public Calibration findImageCalib(IMetadata meta) {
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        System.out.println("XY calibration = " + cal.pixelWidth + ", Z calibration = " + cal.pixelDepth);
        return(cal);
    }
    
    
     /**
     * Find channels name
     * @param imageName
     * @return 
     * @throws loci.common.services.DependencyException
     * @throws loci.common.services.ServiceException
     * @throws loci.formats.FormatException
     * @throws java.io.IOException
     */
    public String[] findChannels (String imageName, IMetadata meta, ImageProcessorReader reader) throws DependencyException, ServiceException, FormatException, IOException {
        ArrayList<String> channels = new ArrayList<>();
        int chs = reader.getSizeC();
        String imageExt =  FilenameUtils.getExtension(imageName);
        switch (imageExt) {
            case "nd" :
                for (int n = 0; n < chs; n++) 
                {
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelName(0, n).toString());
                }
                break;
            case "lif" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null || meta.getChannelName(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelName(0, n).toString());
                break;
            case "czi" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelFluor(0, n).toString());
                break;
            case "ics" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelExcitationWavelength(0, n).value().toString());
                break; 
            case "ics2" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelExcitationWavelength(0, n).value().toString());
                break;        
            default :
                for (int n = 0; n < chs; n++)
                    channels.add(Integer.toString(n));

        }
        channels.add("None");
        return(channels.toArray(new String[channels.size()]));         
    }
    
    
    /**
     * Generate dialog box
     */
    public String dialog(String[] channels) { 
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 80, 0);
        gd.addImage(icon);
          
        gd.addMessage("Channels", new Font(Font.MONOSPACED , Font.BOLD, 12), Color.blue);
        gd.addChoice("Lamin" + ": ", channels, channels[0]);
        
        gd.addNumericField("LoG sigma:", sigma);
        gd.addNumericField("Min nucleus surface (µm2):", minNucSurf);
        gd.addNumericField("Max nucleus surface (µm2):", maxNucSurf);
        
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("XY calibration (µm):", cal.pixelWidth);
        gd.addNumericField("Z calibration (µm):", cal.pixelDepth);
        gd.showDialog();
        
        String ch = gd.getNextChoice();
        
        sigma = gd.getNextNumber();
        minNucSurf = gd.getNextNumber();
        maxNucSurf = gd.getNextNumber();

        cal.pixelWidth = cal.pixelHeight = gd.getNextNumber();
        cal.pixelDepth = gd.getNextNumber();
        pixSurf = cal.pixelWidth*cal.pixelHeight;
        
        if(gd.wasCanceled())
            ch = null;
        
        return(ch);
    }
    
    
    /**
     * Flush and close an image
     */
    public void flush_close(ImagePlus img) {
        img.flush();
        img.close();
    }
    
    
    /**
     * Detect nuclei and compute their 2D shape factors
     */
    public void findNuclei(ImagePlus img, String imgName, String outDir, BufferedWriter results) throws IOException {
        // Detect nuclei
        ImagePlus imgLamin = new Duplicator().run(img);
        IJ.run(imgLamin, "Laplacian of Gaussian", "sigma="+sigma+" scale_normalised negate enhance");
        IJ.setAutoThreshold(imgLamin, "Triangle dark");
        Prefs.blackBackground = false;
        IJ.run(imgLamin, "Convert to Mask", "");
        IJ.run(imgLamin, "Options...", "iterations=4 count=1 do=Close");
        
        // Analyze particles
        ResultsTable rt = new ResultsTable();
        ParticleAnalyzer ana = new ParticleAnalyzer(ParticleAnalyzer.SHOW_ROI_MASKS+ParticleAnalyzer.INCLUDE_HOLES, Measurements.AREA+
                Measurements.SHAPE_DESCRIPTORS, rt, minNucSurf/pixSurf, maxNucSurf/pixSurf);
        ana.analyze(imgLamin);
        WindowManager.getActiveWindow().dispose();
                
        // Draw results in image
        ImagePlus imgOut = ana.getOutputImage();
        imgOut.setCalibration(cal);
        IJ.run(imgOut, "glasbey on dark", "");
        FileSaver ImgCellsFile = new FileSaver(imgOut);
        ImgCellsFile.saveAsTiff(outDir+imgName+"_nuclei_sigma-"+sigma+".tif");
        flush_close(imgOut);
        flush_close(imgLamin);
        
        // Save results in file
        for (int i = 0; i < rt.size(); i++) {
            double area = rt.getValue("Area", i);
            double cir = rt.getValue("Circ.", i);
            double ar = rt.getValue("AR", i);
            double round = rt.getValue("Round", i);
            double sol = rt.getValue("Solidity", i);
            results.write(imgName+"\t"+sigma+"\t"+(i+1)+"\t"+area+"\t"+cir+"\t"+ar+"\t"+round+"\t"+sol+"\n");
            results.flush();
        }
    }
    
}