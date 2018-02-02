/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.nasa.worldwind.geopkg.mosaic;

import gov.nasa.worldwind.geopkg.TileEntry;
import gov.nasa.worldwind.geopkg.TileMatrix;
import it.geosolutions.imageio.stream.input.FileImageInputStreamExtImpl;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.SampleModel;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.media.jai.PlanarImage;
import static org.geotools.resources.image.ImageUtilities.tileImage;
import org.geotools.util.logging.Logging;
import org.opengis.coverage.grid.GridEnvelope;

/**
 *
 * @author Bruce Schubert
 */
public class GeoPackageImageReader extends ImageReader {

    private final static Logger LOGGER = Logging.getLogger(GeoPackageImageReader.class.getPackage().getName());

    /**
     * The {@linkplain GeoPackageReader} that takes care of all the input needed
     * to read raster geopackage files.
     */
    private GeoPackageReader gpkgReader = null;
    private int maxZoomLevel = -1;

    /**
     * the flag defining if there are some listeners attached to this reader.
     */
    private boolean hasListeners;

    /**
     * the {@link ColorModel} to be used for the read raster.
     */
    public ColorModel ccmdl = null;

    /**
     * the {@link SampleModel} associated to this reader.
     */
    private SampleModel csm = null;

    /**
     * the {@link ImageTypeSpecifier} associated to this reader.
     */
    private ImageTypeSpecifier imageType;
    
    /**
     * The .gpkg input file derived from the input source.
     */
    private File file = null;

    /**
     * constructs an {@link ImageReader} able to read grass raster maps.
     *
     * @param originatingProvider the service provider interface for the reader.
     */
    public GeoPackageImageReader(GeoPackageImageReaderSpi originatingProvider) {
        super(originatingProvider);
    }

    /**
     * This method sets the input only if the input object is a supported input
     * type.
     *
     * @param source
     */
    @Override
    public void setInput(Object source, boolean seekForwardOnly, boolean ignoreMetadata) {

        // ImageMosaics pass in GeoPackages as FileImageInputStreamExtImpl
        if (source instanceof FileImageInputStreamExtImpl) {
            //source = ((FileImageInputStreamExtImpl) source).getFile();
            // Base class methods expect input to be an InputStream
            this.input = source;
            // Extract the File object for use by the GeoPackage class
            this.file = ((FileImageInputStreamExtImpl) source).getFile();
            this.seekForwardOnly = false;
            this.ignoreMetadata = true;
            this.minIndex = 0;
        } else {
            // The input is a tile's image data (invoked by the GeoPackageReader)
            super.setInput(input, seekForwardOnly, ignoreMetadata);
        }
    }
//    /**
//     * A progress monitor, set to a dummy one, in the case it is not set by the
//     * user.
//     */
//    private ProgressListener monitor = new DummyProgressListener();

    /**
     * Opens the GeoPackage raster file and obtains the coverage names used for
     * the image index.
     * <p>
     * This method has to be called before any data access, in order to already
     * have the native raster data metadata available.
     * </p>
     *
     * @return <code>true</code> if everything is consistent
     * @throws IIOException
     */
    private void ensureOpen() throws IOException {
        if (gpkgReader == null) {
            gpkgReader = new GeoPackageReader(file, /*hints*/ null);
            maxZoomLevel = gpkgReader.getTileset().getMaxZoomLevel();
        }
    }

    int getZoomLevel(int imageIndex) throws IOException {
        ensureOpen();
        return maxZoomLevel - imageIndex;
    }

    /**
     * Returns the height in pixels of a tile level from the current coverage
     * within the GeoPackage.
     *
     * @param imageIndex overview index (mapped to a tile level)
     * @return the height in pixels
     * @throws IOException
     */
    @Override
    public int getHeight(final int imageIndex) throws IOException {
        ensureOpen();
        GridEnvelope gridRange = gpkgReader.getGridRange(getZoomLevel(imageIndex));
        return gridRange.getSpan(1);
        // The min size returned by toTileSize is 512x512
        //final Dimension tileSize = ImageUtilities.toTileSize(new Dimension(gridRange.getSpan(0), gridRange.getSpan(1)));
        //return tileSize.height;
    }

    /**
     * Returns the width in pixels of a tile level from the current coverage
     * within the GeoPackage.
     *
     * @param imageIndex overview index (mapped to a tile level)
     * @return
     * @throws IOException
     */
    @Override
    public int getWidth(final int imageIndex) throws IOException {
        ensureOpen();
        GridEnvelope gridRange = gpkgReader.getGridRange(getZoomLevel(imageIndex));
        return gridRange.getSpan(0);
    }

    /**
     * Returns the number of zoom levels. in the raster GeoPackage.
     *
     * @param allowSearch ignored
     * @return the number of zoom levels
     * @throws IOException
     */
    @Override
    public int getNumImages(final boolean allowSearch) throws IOException {
        ensureOpen();
        return gpkgReader.getNumOverviews() + 1;
    }

    /**
     * Returns an <code>Iterator</code> containing possible image types to which
     * the given image may be decoded, in the form of
     * <code>ImageTypeSpecifiers</code>s. At least one legal image type will be
     * returned.
     *
     * @param imageIndex the image index (mapped to a zoom level)
     * @return
     * @throws IOException
     */
    @Override
    public synchronized Iterator<ImageTypeSpecifier> getImageTypes(final int imageIndex) throws IOException {
        ensureOpen();

        // TODO: Should imageTypeSpecifiers be a member?
        final List<ImageTypeSpecifier> imageTypeSpecifiers = new ArrayList<>();
        if (imageType == null) {
            csm = gpkgReader.getImageLayout().getSampleModel(null);
            ccmdl = PlanarImage.createColorModel(csm);
            imageType = new ImageTypeSpecifier(ccmdl, csm);
        }
        imageTypeSpecifiers.add(imageType);
        return imageTypeSpecifiers.iterator();
    }

    /**
     * Returns the height of a tile in the given image.
     *
     * @param imageIndex the image index (mapped to a zoom level)
     * @return the tile height in pixels
     * @throws java.io.IOException
     */
    @Override
    public int getTileHeight(int imageIndex) throws IOException {
        ensureOpen();
        TileEntry tileset = gpkgReader.getTileset();
        return tileset.getTileMatricies().get(getZoomLevel(imageIndex)).getTileHeight();
    }

    /**
     * Returns the width of a tile in the given image.
     *
     * @param imageIndex the image index (mapped to a zoom level)
     * @return the width of a tile in pixels
     * @throws java.io.IOException
     */
    @Override
    public int getTileWidth(int imageIndex) throws IOException {
        ensureOpen();
        TileEntry tileset = gpkgReader.getTileset();
        return tileset.getTileMatricies().get(getZoomLevel(imageIndex)).getTileWidth();
    }

    /**
     * Returns if the given image is tiled (true for all raster GeoPackages).
     *
     * @param imageIndex ignored
     * @return true
     * @throws IOException
     */
    @Override
    public boolean isImageTiled(int imageIndex) throws IOException {
        // TODO: test if coverage index is raster layer
        return true;
    }

//    public void setMonitor(ProgressListener monitor) {
//        this.monitor = monitor;
//    }
    /**
     *
     * @param the image index (mapped to a zoom level)
     * @return
     * @throws IOException
     */
    @Override
    public BufferedImage read(final int imageIndex) throws IOException {
        return read(imageIndex, null);
    }

    /**
     *
     * @param imageIndex the image index (mapped to a zoom level)
     * @param param
     * @return
     * @throws IOException
     */
    @Override
    public BufferedImage read(final int imageIndex, ImageReadParam param) throws IOException {
        ensureOpen();

        int zoomLevel = getZoomLevel(imageIndex);
        // Get the pixel width/height of the GeoPackgae image data
        int srcWidth = getWidth(imageIndex);
        int srcHeight = getHeight(imageIndex);
        
        // Compute the pixel region of the source image that should be read, taking into
        // account any source region and subsampling offset settings in the supplied ImageReadParam.
        Rectangle srcRegion = getSourceRegion(param, srcWidth, srcHeight);
        
        // Read GeoPackage tiles for the defined pixel region into an image 
        BufferedImage srcImage = gpkgReader.readTiles(zoomLevel, srcRegion, null);
        
        // Get the BufferedImage to which decoded pixel data should be written. 
        // The image is determined by inspecting the supplied ImageReadParam if it is
        // non-null; if its getDestination method returns a non-null value, that image is
        // simply returned. 
        BufferedImage destImage = getDestination(param, getImageTypes(imageIndex), srcWidth, srcHeight);
        
        // Copy the source image into the destination, scaling/coverting as reqd.
        Graphics2D destGraphics = destImage.createGraphics();
        destGraphics.drawImage(srcImage,
                0, //int dx1,
                0, //int dy1,
                destImage.getWidth(), //int dx2,
                destImage.getHeight(), //int dy2,
                0, //int sx1,
                0, //int sy1,
                srcImage.getWidth(), //int sx2,
                srcImage.getHeight(), //int sy2,
                null); //ImageObserver observer)

        // DEBUGGING: Uncomment block to draw a border around the tiles
        /*
        {
            float thickness = 8;
            destGraphics.setStroke(new BasicStroke(thickness));
            destGraphics.drawRect(0, 0, destImage.getWidth(), destImage.getHeight());
        }
        */
        destGraphics.dispose();
        
        return destImage;
    }

    /**
     * Reads the tile indicated by the <code>tileX</code> and <code>tileY</code>
     * arguments, returning it as a <code>BufferedImage</code>. If the arguments
     * are out of range, an <code>IllegalArgumentException</code> is thrown. If
     * the image is not tiled, the values 0, 0 will return the entire image; any
     * other values will cause an <code>IllegalArgumentException</code> to be
     * thrown.
     *
     * @param imageIndex the image index (mapped to a zoom level)
     * @param tileX
     * @param tileY
     * @return
     * @throws IOException
     */
    @Override
    public BufferedImage readTile(int imageIndex, int tileX, int tileY) throws IOException {
        ensureOpen();
        return this.gpkgReader.readTile(getZoomLevel(imageIndex), tileX, tileY);
    }

    /**
     * Resets this {@link GeoPackageImageReader}.
     */
    @Override
    public void reset() {
        dispose();
        super.setInput(null, false, false);
        gpkgReader = null;
        file = null;
        csm = null;
        imageType = null;
    }

//    /**
//     * Request to abort any current read operation.
//     */
//    @Override
//    public synchronized void abort() {
//        // super.abort();
//        if (gpkgReader != null) {
//            gpkgReader.abort();
//            gpkgReader = null;
//        }
//    }
//    /**
//     * Checks if a request to abort the current read operation has been made.
//     */
//    protected synchronized boolean abortRequested() {
//        return gpkgReader.isAborting();
//    }
    @Override
    public IIOMetadata getStreamMetadata() throws IOException {
        return null;
    }

    @Override
    public IIOMetadata getImageMetadata(int imageIndex) throws IOException {
        return null;
    }
}
