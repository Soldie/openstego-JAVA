/*
 * Steganography utility to hide messages into cover files
 * Author: Samir Vaidya (mailto:syvaidya@gmail.com)
 * Copyright (c) 2007-2008 Samir Vaidya
 */

package net.sourceforge.openstego.plugin.lsb;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;

import net.sourceforge.openstego.*;
import net.sourceforge.openstego.ui.*;
import net.sourceforge.openstego.util.*;
import net.sourceforge.openstego.plugin.template.imagebit.*;

/**
 * Plugin for OpenStego which implements the Least-significant bit algorithm of steganography
 */
public class LSBPlugin extends ImageBitPluginTemplate
{
    /**
     * LabelUtil instance to retrieve labels
     */
    private static LabelUtil labelUtil = LabelUtil.getInstance(LSBPlugin.NAMESPACE);

    /**
     * Constant for Namespace to use for this plugin
     */
    public final static String NAMESPACE = "LSB";

    /**
     * Default constructor
     */
    public LSBPlugin()
    {
        LabelUtil.addNamespace(NAMESPACE, "net.sourceforge.openstego.resource.LSBPluginLabels");
        LSBErrors errors = new LSBErrors(); // Initialize error codes
    }

    /**
     * Gives the name of the plugin
     * @return Name of the plugin
     */
    public String getName()
    {
        return "LSB";
    }

    /**
     * Gives a short description of the plugin
     * @return Short description of the plugin
     */
    public String getDescription()
    {
        return labelUtil.getString("plugin.description");
    }

    /**
     * Method to embed the message into the cover data
     * @param msg Message to be embedded
     * @param msgFileName Name of the message file. If this value is provided, then the filename should be
     *                    embedded in the cover data
     * @param cover Cover data into which message needs to be embedded
     * @param coverFileName Name of the cover file
     * @param stegoFileName Name of the output stego file
     * @return Stego data containing the message
     * @throws OpenStegoException
     */
    public byte[] embedData(byte[] msg, String msgFileName, byte[] cover, String coverFileName, String stegoFileName)
        throws OpenStegoException
    {
        BufferedImage image = null;
        LSBOutputStream lsbOS = null;

        try
        {
            // Generate random image, if input image is not provided
            if(cover == null)
            {
                image = ImageUtil.generateRandomImage(msg.length, ((ImageBitConfig) config).getMaxBitsUsedPerChannel());
            }
            else
            {
                image = ImageUtil.byteArrayToImage(cover, coverFileName);
            }
            lsbOS = new LSBOutputStream(image, msg.length, msgFileName, this.config);
            lsbOS.write(msg);
            lsbOS.close();

            return ImageUtil.imageToByteArray(lsbOS.getImage(), stegoFileName, this);
        }
        catch(IOException ioEx)
        {
            throw new OpenStegoException(ioEx);
        }
    }

    /**
     * Method to extract the message file name from the stego data
     * @param stegoData Stego data containing the message
     * @param stegoFileName Name of the stego file
     * @return Message file name
     * @throws OpenStegoException
     */
    public String extractMsgFileName(byte[] stegoData, String stegoFileName) throws OpenStegoException
    {
        LSBInputStream lsbIS = null;

        lsbIS = new LSBInputStream(ImageUtil.byteArrayToImage(stegoData, stegoFileName), this.config);
        return lsbIS.getDataHeader().getFileName();
    }

    /**
     * Method to extract the message from the stego data
     * @param stegoData Stego data containing the message
     * @param stegoFileName Name of the stego file
     * @return Extracted message
     * @throws OpenStegoException
     */
    public byte[] extractData(byte[] stegoData, String stegoFileName) throws OpenStegoException
    {
        int bytesRead = 0;
        byte[] data = null;
        ImageBitDataHeader header = null;
        LSBInputStream lsbIS = null;

        try
        {
            lsbIS = new LSBInputStream(ImageUtil.byteArrayToImage(stegoData, stegoFileName), this.config);
            header = lsbIS.getDataHeader();
            data = new byte[header.getDataLength()];

            bytesRead = lsbIS.read(data, 0, data.length);
            if(bytesRead != data.length)
            {
                throw new OpenStegoException(NAMESPACE, LSBErrors.ERR_IMAGE_DATA_READ, null);
            }
            lsbIS.close();

            return data;
        }
        catch(OpenStegoException osEx)
        {
            throw osEx;
        }
        catch(Exception ex)
        {
            throw new OpenStegoException(ex);
        }
    }

    /**
     * Method to get the usage details of the plugin
     * @return Usage details of the plugin
     */
    public String getUsage() throws OpenStegoException
    {
        ImageBitConfig defaultConfig = new ImageBitConfig();
        return labelUtil.getString("plugin.usage", new Object[] {
                        new Integer(defaultConfig.getMaxBitsUsedPerChannel()) });
    }
}