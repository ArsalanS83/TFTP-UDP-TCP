package tftp.tcp.server;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

/**
 * TFTP Server Built on TCP
 * @author 105977
 */
public class TFTPTCPServer extends Thread {
    
    protected ServerSocket welcomeSocket;
    protected Socket slaveSocket;
    protected DataInputStream inFromClient;
    protected DataOutputStream outToClient;
    
    // TFTP opcodes
    protected static final byte RRQ [] = new byte[] {0,1};
    protected static final byte WRQ [] = new byte[] {0,2};
    protected static final byte DATA [] = new byte[] {0,3};
    protected static final byte ERROR [] = new byte[] {0,5};
    
    protected String filename; // filename requested by client
    
    // Error Handling
    protected static final String ERROR_MSG = "File not found";
    protected static final byte ERROR_CODE [] = new byte[] {0,1};
    protected static byte SEPARATOR = 0;
    
    
    /**
     * Constructs a TFTP TCP Client
     * Constructs The Welcome Socket
     * @throws IOException
     */
    public TFTPTCPServer() throws IOException
    {
        welcomeSocket = new ServerSocket(9000);
    }
    
    
    
    @Override
    public void run()
    {
        while (true)
        {
            try
            {
                slaveSocket = welcomeSocket.accept();
                
                inFromClient = new DataInputStream(slaveSocket.getInputStream());
                outToClient = new DataOutputStream(slaveSocket.getOutputStream());
                
                // extract opcode from request packet
                byte[] opcode = new byte[2];
                inFromClient.read(opcode,0,2);
                
                // extract FileName
                extractFileName();
                
                // extract Mode
                extractMode();
                
  
                // If Client Sent WRQ
                if (Arrays.equals(opcode,WRQ))
                {   
                    // create file output stream to write data to
                    FileOutputStream fos = new FileOutputStream(filename);
                    
                    int byteRead;
                    
                    while ((byteRead = inFromClient.read()) != -1)
                    {
                        fos.write(byteRead);
                    }
                    
                    fos.close();
                }
                
                // If Client Sent RRQ
                if (Arrays.equals(opcode,RRQ))
                {
                    try
                    {
                        // try to create file input stream
                        FileInputStream fis = new FileInputStream(filename);
                        
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        
                        bos.write(DATA,0,DATA.length); // write opcode to indicate to client that this is data

                        // read data from file
                        // handles unlimited file size   
                        int byteRead;
                        while ((byteRead = fis.read()) != -1)
                        {
                            bos.write(byteRead);
                        }
                        
                        byte[] fileData = bos.toByteArray();
                        
                        // send file data to client
                        outToClient.write(fileData, 0, fileData.length);
                        
                    }
                    catch (FileNotFoundException e)
                    {
                        // create error bytes to send to client
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        os.write(ERROR, 0, ERROR.length);
                        os.write(ERROR_CODE, 0, ERROR_CODE.length);
                        os.write(ERROR_MSG.getBytes("UTF-8"), 0, ERROR_MSG.length());
                        os.write(SEPARATOR);
        
                        byte[] error = os.toByteArray();
                        
                        // send error bytes to client
                        outToClient.write(error,0,error.length);
                    }  
                }    
            }
            catch (IOException ex)
            {
                System.err.println(ex);
            }
        }  
    }
    
    
    /**
     * Extracts File Name Bytes
     * Stores Bytes into Filename String
     * @throws IOException
     */
    public void extractFileName() throws IOException
    {
        // Extract Filename
        ByteArrayOutputStream fileNameBytes = new ByteArrayOutputStream();
        int fileNameByte;
                
        // While haven't reached filename separator
        // read 1 byte at a time
        // Don't know how big filename is!
         while ((fileNameByte = inFromClient.read()) != 0)
         {
             fileNameBytes.write(fileNameByte);
         }
         
        // store filename bytes into filename string
        filename = fileNameBytes.toString();
        
        // finished extracting
        fileNameBytes.close();       
    }
    
    /**
     * Extract TFTP Mode
     * @throws IOException
     */
    public void extractMode() throws IOException
    {
        ByteArrayOutputStream modeBytes = new ByteArrayOutputStream();
        
        int modeByte;
        while ((modeByte = inFromClient.read()) != 0)
        {
            modeBytes.write(modeByte); 
        }
        // mode could be stored, however only read for simplicity
    }
  
    public static void main(String[] args) throws IOException {
        new TFTPTCPServer().start();
        System.out.println("TFTP Server Started"); 
    }
}