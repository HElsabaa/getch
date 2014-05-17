package org.getch

import grails.test.mixin.TestFor
import spock.lang.Specification
import org.apache.commons.io.FileUtils

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(FileSystemTreeService)
class FileSystemTreeServiceSpec extends Specification {

    def setup() {
      //mock the config
      grailsApplication.config.getch.base.directory = System.getProperty("java.io.tmpdir") + '/getchtest' 
      def workdir = grailsApplication.config.getch.base.directory
      //create a single-leg directory hierarchy
      def directory = new File(grailsApplication.config.getch.base.directory + '/common/dc1/mydepartment/myproduct/web/hostname1')
      directory.mkdirs()
      //create a few property files on different levels using the value to inidicate the layer
      new File(directory, 'config.properties').text = """
testkey1=hostname1_testvalue1"""
      new File( workdir + '/common/dc1/mydepartment/myproduct/web/config.properties').text = """
testkey1=web_testvalue1
testkey2=web_testvalue2"""
      new File( workdir + '/common/dc1/mydepartment/myproduct/config.properties').text = """
testkey1=myproduct_testvalue1
testkey2=myproduct_testvalue2
testkey3=myproduct_testvalue3"""
    }

    def cleanup() {
      def workdir = System.getProperty("java.io.tmpdir") + '/getchtest'
      FileUtils.deleteDirectory(new File(workdir))
    }
  
    void "test recursive upwards searching property"(String host, String key, String value) {
      setup:
      def service = new FileSystemTreeService(grailsApplication:grailsApplication)
      expect: 
      service.findValue(host, key) == value
      where:
      host | key || value 
      'hostname1' | 'testkey1' || 'hostname1_testvalue1'
      'hostname1' | 'testkey2' || 'web_testvalue2'
      'hostname1' | 'testkey3' || 'myproduct_testvalue3'
      'hostname1' | 'testkey355' || null
      'hostname2' | 'testkey2' || null
      'hostname2' | 'testkey1' || null
    }

    void "test resursive downwards search for single value"() {
      setup:
      def service = new FileSystemTreeService(grailsApplication:grailsApplication)
      def directory = new File(grailsApplication.config.getch.base.directory + '/common/dc1/mydepartment/myproduct/web/hostname1/somecomponent/')
      directory.mkdirs()
      new File(directory, 'config.properties').text = '''
testkey10=testvalue10
testkey1=hostname1_testvalue1
'''
      expect: 
      service.findValue(host, key) == value
      where:
      host | key || value 
      'hostname1' | 'testkey10' || 'testvalue10'
      'hostname1' | 'testkey1' || 'hostname1_testvalue1'

    }

    void "test search key in yaml file"(String host, String key, String value) {
      setup:
      def service = new FileSystemTreeService(grailsApplication:grailsApplication)
      def yamlFile = new File(grailsApplication.config.getch.base.directory + '/common/dc1/mydepartment/myproduct/config.yaml')
      yamlFile.text='''
testkey4: testvalue4
testkey5: testvalue5 with whitespaces
testkey6:
  - sequencevalue1
  - sequencevalue2
'''
      expect:
      service.findValue(host, key) == value
      where:
      host | key || value 
      'hostname1' | 'testkey4' || 'testvalue4'
      'hostname1' | 'testkey5' || 'testvalue5 with whitespaces'
      'hostname1' | 'testkey6' || 'sequencevalue1,sequencevalue2'
    }

   void "test get encrypted value"() {
      setup:
      def yamlFile = new File(grailsApplication.config.getch.base.directory + '/common/dc1/mydepartment/myproduct/web/hostname1/config.yaml')
      yamlFile.text='''
testkey7: sec:testvalue7
'''
      def fakeTextEncryptor = new Expando()
      fakeTextEncryptor.decrypt = { String st -> return st }
      def service = new FileSystemTreeService(grailsApplication:grailsApplication, textEncryptor: fakeTextEncryptor)
      expect:
      service.findValue('hostname1', 'testkey7') == 'testvalue7'
   }
 
   void "test findValue returns lowest occurance of same key"() {
      setup:
      def service = new FileSystemTreeService(grailsApplication:grailsApplication)
      def directory = new File(grailsApplication.config.getch.base.directory + '/common/dc1/mydepartment/myproduct/web/hostname1/component1/')
      directory.mkdirs()
      new File(directory, 'config.properties').text = '''
testkey11=wrongvalue
'''
      def subdirectory = new File(directory, 'subcomponent1')
      subdirectory.mkdirs()
      new File(subdirectory, 'config.yaml').text = '''
testkey11: rightvalue
'''
      expect:
      service.findValue('hostname1', 'testkey11') == 'rightvalue'
   }

   void "test find filename below hostname"() {
     setup:
     def service = new FileSystemTreeService(grailsApplication:grailsApplication)
     def directory = new File(grailsApplication.config.getch.base.directory + '/common/dc1/mydepartment/myproduct/web/hostname1/blah/')
     directory.mkdirs()
     def file = new File(directory, 'httpd.conf')
     file.text = '''
#blah
'''
     expect:
     //using the name because objects don't work well with SPOCK datatables
     service.findValue('hostname1', 'httpd.conf') == [
       'filename' : 'httpd.conf',
       'content' :  '''
#blah
'''
     ]
   }
 
   void "test find filename above hostname"() {
     setup:
     def service = new FileSystemTreeService(grailsApplication:grailsApplication)
     def directory = new File(grailsApplication.config.getch.base.directory + '/common/dc1/mydepartment/myproduct/web/hostname1/')
     directory.mkdirs()
     def file = new File(grailsApplication.config.getch.base.directory + '/common/dc1/dc_conventions.properties')
     file.text = '''
#blah
'''
     expect:
     //using the name because objects don't work well with SPOCK datatables
     service.findValue('hostname1', 'dc_conventions.properties') == [
       'filename' : 'dc_conventions.properties',
       'content' : '''
#blah
'''
     ]
   
   }

   void "test resolved template file with binding variables"() {
     setup:
     def service = new FileSystemTreeService(grailsApplication:grailsApplication)
     def directory = new File(grailsApplication.config.getch.base.directory + '/common/dc1/mydepartment/myproduct/web/hostname1/component1/')
     directory.mkdirs()
     new File(directory, 'test.conf').text = '''
test1=${testkey101}
test2=<%= testkey102 %>
test3=<%= testkey103.join(',') %>
'''
     new File(grailsApplication.config.getch.base.directory + '/common/dc1/mydepartment/conventions.yaml').text = '''
testkey101: testvalue1
testkey102: testvalue2
testkey103:
  - a
  - b
  - c
'''
     expect:
     service.findValue('hostname1', 'test.conf') == [
       'filename' : 'test.conf',
       'content' : '''
test1=testvalue1
test2=testvalue2
test3=a,b,c
'''

     ]
  
   }

}
