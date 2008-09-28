/*
 * Steganography utility to hide messages into cover files
 * Author: Samir Vaidya (mailto:syvaidya@gmail.com)
 * Copyright (c) 2007-2008 Samir Vaidya
 */

package net.sourceforge.openstego.plugin.dctlsb;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import net.sourceforge.openstego.OpenStegoConfig;
import net.sourceforge.openstego.OpenStegoException;
import net.sourceforge.openstego.plugin.template.dct.DCT;
import net.sourceforge.openstego.plugin.template.dct.DCTDataHeader;
import net.sourceforge.openstego.util.StringUtil;

/**
 * InputStream to read embedded data from image file using DCT LSB algorithm
 */
public class DctLSBInputStream extends InputStream
{
    /**
     * Data header
     */
    private DCTDataHeader dataHeader = null;

    /**
     * Current message bit number
     */
    private int n = 0;

    /**
     * Width of the image
     */
    private int imgWidth = 0;

    /**
     * Height of the image
     */
    private int imgHeight = 0;

    /**
     * Array to store Y component from YUV colorspace of the image
     */
    private int[][] y = null;

    /**
     * Object to handle DCT transforms
     */
    private DCT dct = null;

    /**
     * Array to store the DCT coefficients for the image
     */
    private double[][] dcts = null;

    /**
     * Coordinate hit check class
     */
    private Coordinates coord = null;

    /**
     * Random number generator
     */
    private Random rand = null;

    /**
     * Configuration data
     */
    private OpenStegoConfig config = null;

    /**
     * Default constructor
     * @param image Image data to be read
     * @param config Configuration data to use while reading
     * @throws OpenStegoException
     */
    public DctLSBInputStream(BufferedImage image, OpenStegoConfig config) throws OpenStegoException
    {
        int r = 0;
        int g = 0;
        int b = 0;

        if(image == null)
        {
            throw new IllegalArgumentException("No image file provided");
        }

        this.config = config;
        this.imgWidth = image.getWidth();
        this.imgHeight = image.getHeight();

        // Calculate widht and height rounded to 8
        this.imgWidth = imgWidth - (imgWidth % DCT.NJPEG);
        this.imgHeight = imgHeight - (imgHeight % DCT.NJPEG);

        y = new int[imgWidth][imgHeight];
        for(int i = 0; i < imgWidth; i++)
        {
            for(int j = 0; j < imgHeight; j++)
            {
                r = (image.getRGB(i, j) >> 16) & 0xFF;
                g = (image.getRGB(i, j) >> 8) & 0xFF;
                b = (image.getRGB(i, j)) & 0xFF;

                // Convert RGB to YUV colorspace. Only Y (luminance) component is used for embedding data
                y[i][j] = DCT.pixelRange((0.257 * r) + (0.504 * g) + (0.098 * b) + 16);
            }
        }

        dct = new DCT();
        dct.initDct8x8();
        dct.initQuantumJpegLumin();
        dcts = new double[DCT.NJPEG][DCT.NJPEG];
        coord = new Coordinates((imgWidth * imgHeight * 8) / (DCT.NJPEG * DCT.NJPEG));

        rand = new Random(StringUtil.passwordHash(this.config.getPassword()));
        readHeader();
    }

    /**
     * Method to read header data from the input stream
     * @throws OpenStegoException
     */
    private void readHeader() throws OpenStegoException
    {
        dataHeader = new DCTDataHeader(this, config);
    }

    /**
     * Implementation of <code>InputStream.read()</code> method
     * @return Byte read from the stream
     * @throws IOException
     */
    public int read() throws IOException
    {
        int out = 0;
        int xb = 0;
        int yb = 0;
        int coeffNum = 0;

        for(int count = 0; count < 8; count++)
        {
            if(n >= (imgWidth * imgHeight * 8))
            {
                return -1;
            }

            do
            {
                xb = Math.abs(rand.nextInt()) % (imgWidth / DCT.NJPEG);
                yb = Math.abs(rand.nextInt()) % (imgHeight / DCT.NJPEG);
            }
            while(!coord.add(xb, yb));

            // Do the forward 8x8 DCT of that block
            dct.fwdDctBlock8x8(y, xb * DCT.NJPEG, yb * DCT.NJPEG, dcts);

            // Randomly select a coefficient. Only accept coefficient in the middle frequency range
            do
            {
                coeffNum = (Math.abs(rand.nextInt()) % (DCT.NJPEG * DCT.NJPEG - 2)) + 1;
            }
            while(dct.isMidFreqCoeff8x8(coeffNum) == 0);

            // Quantize block according to quantization quality parameter
            dct.quantize8x8(dcts);

            // Get the LSB of the coefficient
            out = (out << 1) + (((int) dcts[coeffNum / DCT.NJPEG][coeffNum % DCT.NJPEG]) & 1);

            n++;
        }

        return out;
    }

    /**
     * Get method for dataHeader
     * @return Data header
     */
    public DCTDataHeader getDataHeader()
    {
        return dataHeader;
    }
}
