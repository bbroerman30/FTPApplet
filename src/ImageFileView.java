

import java.io.File;
import java.net.MalformedURLException;

import javax.swing.Icon;
import javax.swing.filechooser.FileView;

/* ImageFileView.java is used by FileChooserDemo2.java. */
public class ImageFileView extends FileView {

    public String getName(File f) {
        return null; //let the L&F FileView figure this out
    }

    public String getDescription(File f) {
        return null; //let the L&F FileView figure this out
    }

    public Boolean isTraversable(File f) {
        return null; //let the L&F FileView figure this out
    }

    public String getTypeDescription(File f) {
        String extension = Utils.getExtension(f);
        String type = null;

        if (extension != null) {
            if (extension.equals(Utils.jpeg) ||
                extension.equals(Utils.jpg)) {
                type = "JPEG Image";
            } else if (extension.equals(Utils.gif)){
                type = "GIF Image";
            } else if (extension.equals(Utils.tiff) ||
                       extension.equals(Utils.tif)) {
                type = "TIFF Image";
            } else if (extension.equals(Utils.png)){
                type = "PNG Image";
            }
        }
        return type;
    }

    
    
    public Icon getIcon(File f) {
        String extension = Utils.getExtension(f);
        Icon icon = null;

        try
        {
        if (extension != null) {
            if (extension.equals(Utils.jpeg) ||
                extension.equals(Utils.jpg)) {
                icon = Utils.createImageIcon(f.toURI().toURL());
            } else if (extension.equals(Utils.gif)) {
                icon = Utils.createImageIcon(f.toURI().toURL());
            } else if (extension.equals(Utils.png)) {
                icon = Utils.createImageIcon(f.toURI().toURL());
            }
        }
        }
        catch( MalformedURLException e)
        {
        	
        }
        
        return icon;
    }
}
