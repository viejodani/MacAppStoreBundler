package macappstorebundler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import xmlwise.Plist;
import xmlwise.XmlParseException;

/**
 *
 * @author booldwalker
 */
public class MacAppStoreBundler {
    private final String appFilePath; //The full or relative path of the applucation to sign and pack
    private final String signatureName; //The name of the signature to use
    private final String entitlementFilePath; //The full or relative path of the entitlements file
    private final String packageName; //The name of the package registered in the MacAppStore
    private final String packageFilePath; //The full or relative path of the package file
    private final String categoryType; //The category type for the application
    
    private static final String JAR_EXTENSION = ".jar";
    private static final String DYLIB_EXTENSION = ".dylib";
    private static final String PLIST_EXTENSION = ".plist";
    
    private static final String[] PARTICULAR_FILES = {"jspawnhelper"};
    
    private static final String[] FILES_TO_DELETE = {"libjfxmedia_avf.dylib", "libjfxmedia_qtkit.dylib",
                                                                        "libjfxmedia.dylib", "libjfxwebkit.dylib"};
    
    private String appPlistFileName;
    private String appFileName; //The name of the app without extension
    
    public MacAppStoreBundler(String[] args){
        appFilePath = args[0];
        packageName = args[1];
        signatureName = args[2];
        categoryType = args[3];
        entitlementFilePath = args[4];
        packageFilePath = args[5];
    }
    
    public void packageApp(){
        Log("Verifying app");
        final File appFile = new File(appFilePath);
        
        verifyApp(appFile);
        
        appFileName = appFile.getName().replace(".app", "");
                
        Log("Opening app to sing components and remove libraries");
        cleanAndSignAppComponents(appFile);
        
        Log("Signing app");
        signFile(appFile, true);
        
        Log("Creating package");
        createPackage(appFile);
    }
    
    private void verifyApp(final File appFile){
        if(!appFile.exists()){
            showErrorAndExit(appFile.getName() + " does not exists");
        }
        
        if(!appFile.isDirectory()){
            showErrorAndExit(appFile.getName() + " is not a valid app");
        }
    }
    
    private void cleanAndSignAppComponents(final File folder){
        for(final File entry : folder.listFiles()){
            if(entry.isDirectory()){
                cleanAndSignAppComponents(entry);
            }else{
                processFile(entry);
            }
        }
    }
    
    private void processFile(final File file){
        String fileName = file.getName();
        
        if(fileName.endsWith(JAR_EXTENSION) || isParticularFile(file)){
            signFile(file, true);
        }else if(fileName.endsWith(DYLIB_EXTENSION)){
            if(isFileToDelete(fileName)){
                file.delete();
                Log(String.format("%s has been deleted", file.getName()));
            }else{
                signFile(file, true);
            }
        }else if(fileName.endsWith(PLIST_EXTENSION)){
            editAndSignPlist(file);
        }
    }
    
    private boolean isParticularFile(final File file){
        if(appFileName.equals(file.getName())){
            return true;
        }
        
        for(String particularFile : PARTICULAR_FILES){
            if(particularFile.equals(file.getName())){
                return true;
            }
        }
        
        return false;
    }
    
    private void editAndSignPlist(final File plistFile){
        if(plistFile.getPath().contains("PlugIns")){ // the JDK plist
            processJREPlist(plistFile);
        }else{ //the app plist
            processAppPlist(plistFile);
        }
        
        signFile(plistFile, true);
    }
    
    private void processAppPlist(final File appPlistFile){
        appPlistFileName = appPlistFile.getPath();
        
        try {
            Map<String, Object> plistData = Plist.load(appPlistFile);
            
            //Reinforce the package name, just in case
            if(plistData.containsKey("CFBundleIdentifier")){
                plistData.replace("CFBundleIdentifier", packageName);
            }
            
            //Add the architecture priority
            ArrayList<Object> list = new ArrayList<>();
            list.add("x86_64");
            
            if(!plistData.containsKey("LSArchitecturePriority")){
                plistData.put("LSArchitecturePriority", list);
            }else{
                plistData.replace("LSArchitecturePriority", list);
            }
            
            if(!plistData.containsKey("LSApplicationCategoryType")){
                plistData.put("LSApplicationCategoryType", categoryType);
            }else{
                plistData.replace("LSApplicationCategoryType", categoryType);
            }
            
            Plist.store(plistData, appPlistFile);
        } catch (XmlParseException | IOException ex) {
            Logger.getLogger(MacAppStoreBundler.class.getName()).log(Level.SEVERE, null, ex);
            showErrorAndExit(ex.getMessage());
        }
    }
    
    private void processJREPlist(final File jrePlistFile){
        try {
            Map<String, Object> plistData = Plist.load(jrePlistFile);
            
            //Edit the bindle identifier of the JRE plist
            if(plistData.containsKey("CFBundleIdentifier")){
                plistData.replace("CFBundleIdentifier", packageName + ".jre");
            }
            
            Plist.store(plistData, jrePlistFile);
        } catch (XmlParseException | IOException ex) {
            Logger.getLogger(MacAppStoreBundler.class.getName()).log(Level.SEVERE, null, ex);
            showErrorAndExit(null);
        }
    }
 
    private boolean isFileToDelete(final String fileName){
        for(String fileToDelete : FILES_TO_DELETE){
            if(fileName.equals(fileToDelete)){
                return true;
            }
        }
        
        return false;
    }
    
    private void createPackage(final File appFile){
        String[] command = {"productbuild", "--component", appFilePath, "/Applications", packageFilePath, "--sign", 
                                     String.format("3rd Party Mac Developer Installer: %s", signatureName), "--product", appPlistFileName};
        
        runCommand(command, true);
        
        Log(String.format("%s has been packaged into %s", appFile.getName(), new File(packageFilePath).getName()));
    }
    
    private void signFile(final File file, final boolean withEntitlement){
        runCommand(getCommand(file.getPath(), withEntitlement), false);
        
        Log(String.format("%s has been signed", file.getName()));
    }
    
    private String[] getCommand(final String filePath, final boolean withEntitlement){
        String signature = String.format("3rd Party Mac Developer Application: %s", signatureName);
        
        if(withEntitlement){
            String[] commands = {"codesign", "-f", "-s", signature, "--entitlements",
                                            entitlementFilePath, filePath};
            
            return commands;
        }
        
        String[] commands = {"codesign", "-f", "-s", signature, filePath};

        return commands;
    }
    
    private void runCommand (String[] command, final boolean showInput){
         try {
            Runtime runtime = Runtime.getRuntime();
            
            final Process process = runtime.exec(command);
            
            new Thread(() -> {
                BufferedReader input = new BufferedReader(new InputStreamReader(showInput ? process.getInputStream() : process.getErrorStream()));
                String line;
                
                try{
                    while((line = input.readLine()) != null){
                        Log(line);
                    }
                }catch(IOException ex){
                    Log(ex.getMessage());
                }
            }).start();
            
            process.waitFor();
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(MacAppStoreBundler.class.getName()).log(Level.SEVERE, null, ex);
            showErrorAndExit(ex.getMessage());
        }
    }
    
    private void showErrorAndExit(final String errorMessage){
        if(errorMessage != null){
            Logger.getLogger(MacAppStoreBundler.class.getName()).log(Level.SEVERE, errorMessage);
        }
        
        System.exit(0);
    }
    
    private void Log(String logMessage){
        Logger.getLogger(MacAppStoreBundler.class.getName()).log(Level.INFO, logMessage);
    }
    
    private static void PrintErrors(final String[] errors){
        for(String error : errors){
            Logger.getLogger(MacAppStoreBundler.class.getName()).log(Level.SEVERE, error);
        }
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        String[] errors = getArgumentErrors(args);
        
        if(null != errors){
            PrintErrors(errors);
        }else{
            MacAppStoreBundler bundler = new MacAppStoreBundler(args);
            bundler.packageApp();
        }
    }
    
    private static String[] getArgumentErrors(String[] args){
        StringBuilder sb = new StringBuilder();
        
        if(args.length !=6){
            sb.append("Only six parameters are allowed,");
        }
        
        if(!args[0].endsWith(".app")){
            sb.append("First argument must be an app fileName,");
        }
        
        if(!args[4].endsWith(".plist")){
            sb.append("First argument must be a plist fileName,");
        }
        
        if(!args[5].endsWith(".pkg")){
            sb.append("First argument must be an osx package fileName,");
        }
        
        if(sb.length() > 0){
            return sb.toString().split(","); 
        }
        
        return null;
    }
}
