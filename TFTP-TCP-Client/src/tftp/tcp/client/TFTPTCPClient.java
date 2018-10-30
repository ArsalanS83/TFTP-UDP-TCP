 package tftp.tcp.client;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;

/**
 * TFTP Client Built on TCP
 * @author 105977
 */
public class TFTPTCPClient {
    
    protected Socket clientSocket;
    protected DataOutputStream outToServer;
    protected DataInputStream inFromServer;
    
    protected String filename;
    
    // TFTP opcodes
    protected static final byte WRQ [] = new byte[] {0,2};
    protected static final byte DATA [] = new byte[] {0,3};
    protected static final byte RRQ [] = new byte[] {0,1};
    protected static final byte ERROR [] = new byte[] {0,5};
   
    protected static byte SEPARATOR = 0;


    /**
     * TCP Client Menu
     * @throws IOException
     */
    public void run() throws IOException
    {
        Scanner input = new Scanner(System.in);
        int inputNum = 0;
        
        while (inputNum != 3)
        {
             System.out.println("********************************");
             System.out.println("TFTP Application Menu");
             System.out.println("********************************");
             System.out.println("Press 1 To Store a File");
             // user wants to store file on server
             System.out.println("Press 2 To Retrieve a File");
             // user wants to retrieve file from server
             System.out.println("Press 3 To Quit");
             // Quit
             System.out.println("**********************");
             System.out.print("Enter Choice:");
             
             inputNum = input.nextInt();
             
             Scanner fileInput = new Scanner(System.in);
             
             switch (inputNum)
             {
                 case 1:
                     System.out.print("Enter file name to store:");
                     createSocket();
                     filename = fileInput.nextLine();
                     sendRequest(WRQ,filename,"octet");
                     sendToServer(); // send file data to server
                     break;
                     
                 case 2:
                     System.out.print("Enter file name to retrieve:");
                     createSocket();
                     filename = fileInput.nextLine();
                     sendRequest(RRQ,filename,"octet");
                     writeToFile(); // read file data sent from server
                     break;
                     
                 case 3:
                     clientSocket.close();
                     System.exit(0);
             }
        }
    }
    
    
    /**
     * Sends a Read/Write Packet
     * @param opCode The opCode is 01
     * @param filename The desired filename set by the user
     * @param mode mode of TFTP
     * @throws java.io.UnsupportedEncodingException If UTF-8 is not supported
     */
    public void sendRequest(byte[] opCode, String filename, String mode) throws UnsupportedEncodingException, IOException
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        
        os.write(opCode, 0, opCode.length);
        os.write(filename.getBytes("UTF-8"), 0, filename.getBytes().length);
        os.write(SEPARATOR);   
        os.write(mode.getBytes("UTF-8"),0,mode.getBytes().length);
        os.write(SEPARATOR); 
        
        byte[] requestPacket = os.toByteArray();
        
        outToServer.write(requestPacket,0,requestPacket.length);
    }
    
    /**
     * Sends The File Data To Server
     * @throws IOException
     */
    public void sendToServer() throws IOException
    {
        try
        {
            // create the input stream to read file data
            FileInputStream fis = new FileInputStream(filename);
            
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            
            // start reading
            // handles unlimited file size
            int byteRead;
            while ((byteRead = fis.read()) != -1)
            {
                bos.write(byteRead);
            }
            
            // convert data to byte array and send
            byte[] fileData = bos.toByteArray();
            
            outToServer.write(fileData,0, fileData.length);

            System.out.println("The file " + filename + " has been transferred"); 
            
            clientSocket.close();
        }
        catch (FileNotFoundException ex)
        {
            System.out.println("File Not Found on Client!"); 
        }
    }
    
    /**
     * Writes Data Sent from Server To File
     * @throws IOException
     */
    public void writeToFile() throws IOException
    {   
        // extract opcode
        byte[] opcode = new byte[2];
        
        inFromServer.read(opcode,0,2);
        
        // File Not Found on Server
        if (Arrays.equals(opcode,ERROR))
        {
            int available = inFromServer.available();      
            byte[] errorInfo = new byte[available];   // error info as byte array
            inFromServer.read(errorInfo,0,errorInfo.length);
            
            // error info could be extracted to identify error message
            // however, only read for simplicity
            
            System.out.println("File Not Found on Server");
            
        }
        else
        {
            // Create File To Write Data To
            FileOutputStream fos = new FileOutputStream(filename);
            
            // read remaining bytes (file data)
            int available = inFromServer.available();
            byte[] fileData = new byte[available];
            
            inFromServer.read(fileData, 0, fileData.length);
            
            // write the file data to the file
            fos.write(fileData,0,fileData.length);

            fos.close();
            
            System.out.println("The file " + filename + " has been stored ");
           
            clientSocket.close();
        }   
    }
    
    /**
     * Constructs a Socket
     * Creates Input and Output Streams
     * @throws IOException
     */
    public void createSocket() throws IOException
    {
        clientSocket = new Socket("127.0.0.1",9000);
        outToServer = new DataOutputStream(clientSocket.getOutputStream());
        inFromServer = new DataInputStream(clientSocket.getInputStream());
    }

    public static void main(String[] args) throws IOException
    {
        new TFTPTCPClient().run();
    }   
}