package org.getch

/**
 * Implementation of a Reader for Java Properties files
 *
 * @author Robert Krombholz
 */
class PropertyFileReader implements FileReader {
  //used to implement the Chain of Responsibilities pattern
  private FileReader nextReader
  public PropertyFileReader(FileReader reader) {
    nextReader = reader
  }
  
  /**
   * returns the value of the fiven Key from the File 
   * specified in the constructor
   * @param file The file to lookup the key in
   * @param key The key to load from the Properties file
   */
  public String getValueForKey(File file, String key) {
    if (file.name.endsWith('.properties')) {
      def props = new Properties()
      props.load(file.newInputStream())
      return props."$key"
    }
    else {
      return nextReader.getValueForKey(file, key)
    }
  }

  public String getMimeType() {
    // the type text/x-java-properties exists but is not widely used
    return 'text/plain'
  }
}

