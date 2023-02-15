package Nucleus_Forms;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.Duplicator;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.util.ImageProcessorReader;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import java.io.FileWriter;
import java.util.ArrayList;
import loci.common.Region;
import loci.plugins.in.ImporterOptions;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;


/*
 * Detect lamin-marked nuclei and compute their 2D shape factors
 *
 * @author phm
 */
public class Nucleus_Forms implements PlugIn {
    
    Nucleus_Forms_Tools.Tools tools = new Nucleus_Forms_Tools.Tools();
    private String imageDir = "";
    public  String outDirResults = "";
    public  String rootName = "";
    public BufferedWriter results, globalResults;
    
    public void run(String arg) {
        try {     
            imageDir = IJ.getDirectory("Choose directory containing image files...");
            if (imageDir == null) {
                return;
            }
            
            // Find images with nd extension
            ArrayList<String> imageFile = tools.findImages(imageDir, "nd");
            if (imageFile == null) {
                IJ.showMessage("Error", "No images found with nd extension");
                return;
            }
            
            // Create output folder
            outDirResults = imageDir + File.separator + "Results" + File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }
          
            // Write headers in results file
            String header = "Image name\tNucleus ID\tNucleus surface (µm2)\tNucleus circularity\tNucleus aspect ratio\tNucleus roundness\tNucleus solidity\n";
            FileWriter fwResults = new FileWriter(outDirResults + "results_sigma-"+tools.sigma+".xls", false);
            results = new BufferedWriter(fwResults);
            results.write(header);
            results.flush();
            
            // Create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            reader.setId(imageFile.get(0));
            
            // Find image calibration
            tools.cal = tools.findImageCalib(meta);
            
            // Find channel names
            String[] channels = tools.findChannels(imageFile.get(0), meta, reader);
            
            // Channels dialog
            String laminCh = tools.dialog(channels);
            if (laminCh == null) {
                IJ.showStatus("Plugin canceled");
                return;
            }
            
            for (String f : imageFile) {
                rootName = FilenameUtils.getBaseName(f);
                System.out.println("--- ANALYZING IMAGE " + rootName + " ------");
                reader.setId(f);
                
                String roiFile = imageDir + File.separator + rootName + ".roi";
                RoiManager rm = new RoiManager(false);
                Roi roi = null;
                if (new File(roiFile).exists()) {
                    IJ.showStatus("ROI file found, analysis will be performed in it");
                    rm.runCommand("Open", roiFile);
                    roi = rm.getRoi(0);
                }
                
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setCrop(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                if (roi != null) {
                    options.setCrop(true);
                    Region reg = new Region(roi.getBounds().x, roi.getBounds().y, roi.getBounds().width, roi.getBounds().height);
                    options.setCropRegion(0, reg);
                    options.doCrop();
                }

                // Open Lamin channel
                System.out.println("- Analyzing " + laminCh + " channel -");
                int indexCh = ArrayUtils.indexOf(channels, laminCh);
                ImagePlus img = BF.openImagePlus(options)[indexCh];
                
                // Keep middle z-slice only
                ImagePlus imgLamin = new Duplicator().run(img, img.getNSlices()/2, img.getNSlices()/2);
                
                // Find nuclei and compute parameters
                tools.findNuclei(imgLamin, rootName, outDirResults, results);
            }            
            results.close();
        } catch (IOException | DependencyException | ServiceException | FormatException  ex) {
            Logger.getLogger(Nucleus_Forms.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("--- All done! ---");
    }
}

           