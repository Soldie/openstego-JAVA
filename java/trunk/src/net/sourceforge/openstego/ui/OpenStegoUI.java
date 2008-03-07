/*
 * Steganography utility to hide messages into cover files
 * Author: Samir Vaidya (mailto:syvaidya@gmail.com)
 * Copyright (c) 2007-2008 Samir Vaidya
 */

package net.sourceforge.openstego.ui;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;

import net.sourceforge.openstego.*;
import net.sourceforge.openstego.util.*;

/**
 * This is the main class for OpenStego GUI and it implements the action and window listeners.
 */
public class OpenStegoUI extends OpenStegoFrame
{
    /**
     * LabelUtil instance to retrieve labels
     */
    private static LabelUtil labelUtil = LabelUtil.getInstance(OpenStego.NAMESPACE);

    /**
     * Static variable to holds path to last selected folder
     */
    private static String lastFolder = null;

    /**
     * Class variable to store OpenStego config data
     */
    private OpenStegoConfig config = null;

    /**
     * Class variable to store stego plugin object
     */
    private OpenStegoPlugin plugin = null;

    /**
     * Reference to the UI Panel specific to the plugin
     */
    protected PluginEmbedOptionsUI pluginEmbedOptionsUI = null;

    /**
     * Default constructor
     * @throws OpenStegoException
     */
    public OpenStegoUI() throws OpenStegoException
    {
        super();

        // Populate the combo box with list of algorithm plugins available
        extractAlgoComboBox.addItem(labelUtil.getString("gui.label.plugin.auto"));
        List algoList = PluginManager.getPluginNames();
        for(int i = 0; i < algoList.size(); i++)
        {
            embedAlgoComboBox.addItem(algoList.get(i));
            extractAlgoComboBox.addItem(algoList.get(i));
        }

        resetGUI();

        URL iconURL = getClass().getResource("/image/OpenStegoIcon.png");
        if(iconURL != null)
        {
            this.setIconImage(new ImageIcon(iconURL).getImage());
        }

        Listener listener = new Listener();
        addWindowListener(listener);
        okButton.addActionListener(listener);
        cancelButton.addActionListener(listener);
        msgFileButton.addActionListener(listener);
        coverFileButton.addActionListener(listener);
        stegoFileButton.addActionListener(listener);
        inputStegoFileButton.addActionListener(listener);
        outputFolderButton.addActionListener(listener);

        // "Esc" key handling
        KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
        Action escapeAction = new AbstractAction()
        {
        	public void actionPerformed(ActionEvent ev)
        	{
        		close();
        	}
        };

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escapeKeyStroke, "ESCAPE");
        getRootPane().getActionMap().put("ESCAPE", escapeAction);
    }

    /**
     * Method to reset the GUI components from scratch
     * @throws OpenStegoException
     */
    private void resetGUI() throws OpenStegoException
    {
        plugin = PluginManager.getPluginByName((String) embedAlgoComboBox.getSelectedItem());
        config = plugin.createConfig();

        // Remove the existing UI object
        pluginEmbedOptionsPanel.removeAll();

        // Get UI object for plugin
        pluginEmbedOptionsUI = plugin.getEmbedOptionsUI(this);
        if(pluginEmbedOptionsUI != null)
        {
            pluginEmbedOptionsPanel.setLayout(new GridLayout(1, 1));
            pluginEmbedOptionsPanel.add(pluginEmbedOptionsUI);
            pluginEmbedOptionsPanel.setVisible(true);
        }
        else
        {
            pluginEmbedOptionsPanel.setVisible(false);
        }

        setGUIFromConfig();
        pack();
        setResizable(false);

        msgFileTextField.setText("");
        coverFileTextField.setText("");
        stegoFileTextField.setText("");
        passwordTextField.setText("");
        confPasswordTextField.setText("");
        msgFileTextField.requestFocus();
    }

    /**
     * Method to handle change event for 'embedAlgoComboBox'
     */
    protected void embedAlgoChanged() throws OpenStegoException
    {
        resetGUI();
    }

    /**
     * This method embeds the selected data file into selected image file
     * @throws OpenStegoException
     */
    private void embedData() throws OpenStegoException
    {
        OpenStego openStego = null;
        byte[] stegoData = null;
        String dataFileName = null;
        String imgFileName = null;
        String outputFileName = null;
        String password = null;
        String confPassword = null;
        File outputFile = null;

        dataFileName = msgFileTextField.getText();
        imgFileName = coverFileTextField.getText();
        outputFileName = stegoFileTextField.getText();
        password = new String(passwordTextField.getPassword());
        confPassword = new String(confPasswordTextField.getPassword());

        // START: Input Validations
        if(!checkMandatory(msgFileTextField, labelUtil.getString("gui.label.msgFile"))) return;
        if(!checkMandatory(coverFileTextField, labelUtil.getString("gui.label.coverFile"))) return;
        if(!checkMandatory(stegoFileTextField, labelUtil.getString("gui.label.outputStegoFile"))) return;

        if(useEncryptCheckBox.isSelected())
        {
            if(!checkMandatory(passwordTextField, labelUtil.getString("gui.label.option.password"))) return;
            if(!checkMandatory(confPasswordTextField, labelUtil.getString("gui.label.option.confPassword"))) return;
            if(!password.equals(confPassword))
            {
                JOptionPane.showMessageDialog(this, labelUtil.getString("gui.msg.err.passwordMismatch"),
                    labelUtil.getString("gui.msg.title.err"), JOptionPane.ERROR_MESSAGE);
                confPasswordTextField.requestFocus();
                return;
            }
        }

        if(!pluginEmbedOptionsUI.validateEmbedAction()) return;
        // END: Input Validations

        setConfigFromGUI();
        openStego = new OpenStego(plugin, config);
        stegoData = openStego.embedData(dataFileName == null || dataFileName.equals("") ? null : new File(dataFileName),
                                        imgFileName == null || imgFileName.equals("") ? null : new File(imgFileName),
                                        outputFileName);
        outputFile = new File(outputFileName);

        if(outputFile.exists())
        {
            if(JOptionPane.showConfirmDialog(this, labelUtil.getString("gui.msg.warn.fileExists",
                    new Object[] { outputFileName }), labelUtil.getString("gui.msg.title.warn"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.NO_OPTION)
            {
                return;
            }
        }

        CommonUtil.writeFile(stegoData, outputFileName);
        JOptionPane.showMessageDialog(this, labelUtil.getString("gui.msg.success.embed"),
                labelUtil.getString("gui.msg.title.success"), JOptionPane.INFORMATION_MESSAGE);

        //Reset configuration
        resetGUI();
    }

    /**
     * This method extracts data from the selected image file
     * @throws OpenStegoException
     */
    private void extractData() throws OpenStegoException
    {
        OpenStego openStego = null;
        OpenStegoConfig config = null;
        OpenStegoPlugin extractPlugin = null;
        String imgFileName = null;
        String outputFolder = null;
        String outputFileName = null;
        File file = null;
        List stegoOutput = null;

        if(extractAlgoComboBox.getSelectedIndex() > 0)
        {
            extractPlugin = PluginManager.getPluginByName((String) extractAlgoComboBox.getSelectedItem());
            config = extractPlugin.createConfig();
        }

        openStego = new OpenStego(extractPlugin, config);
        config = openStego.getConfig();
        config.setPassword(new String(extractPwdTextField.getPassword()));
        imgFileName = inputStegoFileTextField.getText();
        outputFolder = outputFolderTextField.getText();

        // START: Input Validations
        if(!checkMandatory(inputStegoFileTextField, labelUtil.getString("gui.label.inputStegoFile"))) return;
        if(!checkMandatory(outputFolderTextField, labelUtil.getString("gui.label.outputDataFolder"))) return;
        // END: Input Validations

        stegoOutput = openStego.extractData(new File(imgFileName));
        outputFileName = (String) stegoOutput.get(0);
        file = new File(outputFolder + File.separator + outputFileName);
        if(file.exists())
        {
            if(JOptionPane.showConfirmDialog(this, labelUtil.getString("gui.msg.warn.fileExists",
                    new Object[] { outputFileName }), labelUtil.getString("gui.msg.title.warn"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.NO_OPTION)
            {
                return;
            }
        }

        CommonUtil.writeFile((byte[]) stegoOutput.get(1), outputFolder + File.separator + outputFileName);
        JOptionPane.showMessageDialog(this, labelUtil.getString("gui.msg.success.extract", new Object[] {
                outputFileName }), labelUtil.getString("gui.msg.title.success"), JOptionPane.INFORMATION_MESSAGE);

        this.inputStegoFileTextField.setText("");
        this.outputFolderTextField.setText("");
        this.extractPwdTextField.setText("");
        this.inputStegoFileTextField.requestFocus();
    }

    /**
     * This method shows the file chooser and updates the text field based on the selection
     * @throws OpenStegoException
     */
    private void selectFile(String action) throws OpenStegoException
    {
        FileBrowser browser = new FileBrowser();
        String fileName = null;
        String title = null;
        String filterDesc = null;
        boolean dirOnly = false;
        List allowedExts = null;
        JTextField textField = null;

        if(action.equals("BROWSE_SRC_DATA"))
        {
            title = labelUtil.getString("gui.filechooser.title.msgFile");
            textField = this.msgFileTextField;
        }
        else if(action.equals("BROWSE_SRC_IMG"))
        {
            title = labelUtil.getString("gui.filechooser.title.coverFile");
            filterDesc = labelUtil.getString("gui.filechooser.filter.coverFiles", new Object[] {
                                                                                getExtensionsString("R") });
            allowedExts = getExtensionsList("R");
            textField = this.coverFileTextField;
        }
        else if(action.equals("BROWSE_TGT_IMG"))
        {
            title = labelUtil.getString("gui.filechooser.title.outputStegoFile");
            filterDesc = labelUtil.getString("gui.filechooser.filter.stegoFiles", new Object[] {
                                                                                getExtensionsString("W") });
            allowedExts = getExtensionsList("W");
            textField = this.stegoFileTextField;
        }
        else if(action.equals("BROWSE_IMG_FOR_EXTRACT"))
        {
            title = labelUtil.getString("gui.filechooser.title.inputStegoFile");
            filterDesc = labelUtil.getString("gui.filechooser.filter.stegoFiles", new Object[] {
                                                                                getExtensionsString("W") });
            allowedExts = getExtensionsList("W");
            textField = this.inputStegoFileTextField;
        }
        else if(action.equals("BROWSE_TGT_DATA"))
        {
            title = labelUtil.getString("gui.filechooser.title.outputDataFolder");
            dirOnly = true;
            textField = this.outputFolderTextField;
        }

        fileName = browser.getFileName(title, filterDesc, dirOnly, allowedExts);
        if(fileName != null)
        {
            // Check for valid extension for output file (in case of BROWSE_TGT_IMG)
            if(action.equals("BROWSE_TGT_IMG"))
            {
                if(!plugin.getWritableFileExtensions().contains(fileName.substring(
                            fileName.lastIndexOf('.') + 1).toLowerCase()))
                {
                    fileName = fileName + "." + plugin.getWritableFileExtensions().get(0);
                }
            }
            textField.setText(fileName);
        }
    }

    /**
     * This method exits the application.
     */
    private void close()
    {
        System.exit(0);
    }

    /**
     * Method to set the config items in GUI
     * @throws OpenStegoException
     */
    private void setGUIFromConfig() throws OpenStegoException
    {
        useCompCheckBox.setSelected(config.isUseCompression());
        useEncryptCheckBox.setSelected(config.isUseEncryption());

        if(pluginEmbedOptionsUI != null)
        {
            pluginEmbedOptionsUI.setGUIFromConfig(config);
        }
    }

    /**
     * Method to load the config items from GUI
     * @throws OpenStegoException
     */
    private void setConfigFromGUI() throws OpenStegoException
    {
        config.setUseCompression(useCompCheckBox.isSelected());
        config.setUseEncryption(useEncryptCheckBox.isSelected());
        config.setPassword(new String(passwordTextField.getPassword()));

        if(pluginEmbedOptionsUI != null)
        {
            pluginEmbedOptionsUI.setConfigFromGUI(config);
        }
    }

    /**
     * This method handles all the exceptions in the GUI
     * @param ex Exception to be handled
     */
    private void handleException(Exception ex)
    {
        String msg = ex.getMessage();

        if ((msg == null) || (msg.trim().equals("")))
        {
            StringWriter writer = new StringWriter();
            ex.printStackTrace(new PrintWriter(writer));
            msg = writer.toString();
        }

        ex.printStackTrace();
        JOptionPane.showMessageDialog(this, msg,
                labelUtil.getString("gui.msg.title.err"), JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Method to check whether value is provided or not; and display message box in case it is not provided
     * @param textField Text field to be checked for value
     * @param fieldName Name of the field
     * @return Flag whether value exists or not
     */
    private boolean checkMandatory(JTextField textField, String fieldName)
    {
        if(!textField.isEnabled())
        {
            return true;
        }

        String value = textField.getText();
        if(value == null || value.trim().equals(""))
        {
            JOptionPane.showMessageDialog(this, labelUtil.getString("gui.msg.err.mandatoryCheck",
                    new Object[] { fieldName }), labelUtil.getString("gui.msg.title.err"),
                    JOptionPane.ERROR_MESSAGE);

            textField.requestFocus();
            return false;
        }

        return true;
    }

    /**
     * Method to get the list of image extensions as a single string
     * @param flag Flag to indicate whether readable ("R") or writeable ("W") extensions are required
     * @return List of image extensions (as string)
     * @throws OpenStegoException
     */
    private String getExtensionsString(String flag) throws OpenStegoException
    {
        List list = null;
        StringBuffer output = new StringBuffer();

        if(flag.equals("R"))
        {
            list = plugin.getReadableFileExtensions();
        }
        else if(flag.equals("W"))
        {
            list = plugin.getWritableFileExtensions();
        }

        for(int i = 0; i < list.size(); i++)
        {
            if(i > 0)
            {
                output.append(", ");
            }
            output.append("*.").append(list.get(i));
        }
        return output.toString();
    }

    /**
     * Method to get the list of image extensions as a list
     * @param flag Flag to indicate whether readable ("R") or writeable ("W") extensions are required
     * @return List of image extensions (as list)
     * @throws OpenStegoException
     */
    private List getExtensionsList(String flag) throws OpenStegoException
    {
        List list = null;
        List output = new ArrayList();

        if(flag.equals("R"))
        {
            list = plugin.getReadableFileExtensions();
        }
        else if(flag.equals("W"))
        {
            list = plugin.getWritableFileExtensions();
        }

        for(int i = 0; i < list.size(); i++)
        {
            output.add("." + list.get(i));
        }
        return output;
    }

    /**
     * Common listener class to handlw action and window events
     */
    class Listener implements ActionListener, WindowListener
    {
        public void actionPerformed(ActionEvent ev)
        {
            try
            {
                String action = ev.getActionCommand();

                if(action.startsWith("BROWSE_"))
                {
                    selectFile(action);
                }
                else if(action.equals("OK"))
                {
                    if(mainTabbedPane.getSelectedIndex() == 0) // Embed tab
                    {
                        embedData();
                    }
                    else // Extract tab
                    {
                        extractData();
                    }
                }
                else if(action.equals("CANCEL"))
                {
                    close();
                }
            }
            catch(Exception ex)
            {
                handleException(ex);
            }
        }

        public void windowClosing(WindowEvent ev)
        {
            close();
        }

        public void windowActivated(WindowEvent ev) {}
        public void windowClosed(WindowEvent ev) {}
        public void windowDeactivated(WindowEvent ev) {}
        public void windowDeiconified(WindowEvent ev) {}
        public void windowIconified(WindowEvent ev) {}
        public void windowOpened(WindowEvent ev) {}
    }

    /**
     * Class to implement File Chooser
     */
    class FileBrowser
    {
        /**
         * Method to get the display file chooser and return the selected file name
         * @param dialogTitle Title for the file chooser dialog box
         * @param filterDesc Description to be displayed for the filter in file chooser
         * @param dirOnly Flag to indicate whether only directory selection should be allowed
         * @param allowedExts Allowed file extensions for the filter
         * @return Name of the selected file (null if no file was selected)
         */
        public String getFileName(String dialogTitle, String filterDesc, boolean dirOnly, List allowedExts)
        {
            int retVal = 0;
            String fileName = null;

            JFileChooser chooser = new JFileChooser(lastFolder);
            if(dirOnly)
            {
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            }

            if(filterDesc != null)
            {
                chooser.setFileFilter(new FileBrowserFilter(filterDesc, allowedExts));
            }
            chooser.setDialogTitle(dialogTitle);
            retVal = chooser.showOpenDialog(null);

            if(retVal == JFileChooser.APPROVE_OPTION)
            {
                fileName = chooser.getSelectedFile().getPath();
                lastFolder = chooser.getSelectedFile().getParent();
            }

            return fileName;
        }

        /**
         * Class to implement filter for file chooser
         */
        class FileBrowserFilter extends FileFilter
        {
            /**
             * Description of the filter
             */
            private String filterDesc = null;

            /**
             * List of allowed file extensions
             */
            private List allowedExts = null;

            /**
             * Default constructor
             * @param filterDesc Description of the filter
             * @param allowedExts List of allowed file extensions
             */
            public FileBrowserFilter(String filterDesc, List allowedExts)
            {
                this.filterDesc = filterDesc;
                this.allowedExts = allowedExts;
            }

            /**
             * Implementation of <code>accept</accept> method of <code>FileFilter</code> class
             * @param file File to check whether it is acceptable by this filter or not
             * @return Flag to indicate whether file is acceptable or not
             */
            public boolean accept(File file)
            {
                if(file != null)
                {
                    if(allowedExts == null || allowedExts.size() == 0 || file.isDirectory())
                    {
                        return true;
                    }

                    for(int i = 0; i < allowedExts.size(); i++)
                    {
                        if(file.getName().toLowerCase().endsWith(allowedExts.get(i).toString()))
                        {
                            return true;
                        }
                    }
                }

                return false;
            }

            /**
             * Implementation of <code>getDescription</accept> method of <code>FileFilter</code> class
             * @return Description of the filter
             */
            public String getDescription()
            {
                return filterDesc;
            }
        }
    }
}
