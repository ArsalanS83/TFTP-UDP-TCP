package tftp.udp.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;

/**
 * TFTP Client Built on UDP
 * @author 105977
 */
public class TFTPUDPClient {
    
    protected DatagramSocket socket;
    protected InetAddress IPAddress;
    
    protected String filename; // filename provided by Client to store/retrieve
    
    // TFTP Packet opcodes
    protected static final byte WRQ [] = new byte[] {0,2};
    protected static final byte DATA [] = new byte[] {0,3};
    protected static final byte RRQ [] = new byte[] {0,1};
    protected static final byte ACK [] = new byte[] {0,4};
    protected static final byte ERROR [] = new byte[] {0,5};
    protected static byte SEPARATOR = 0;
    
    protected DatagramPacket packet;
    protected DatagramPacket receivedPacket;
    
    protected byte[] blockNumber = new byte[] {0,0};
    
    protected int TFTP_PORT = 9000; // port 69 would throw an exception
    
    protected static int MAX_RETRIES = 10; // max number of retransmits on socket timeout
    protected int retries; // current number of retransmit attempts
    
    protected FileOutputStream fos; // File Output Stream to Write File Data To
    
    
    /**
     * Constructs a UDP Client
     * Creates a Socket
     * @throws java.net.SocketException If Socket can't be constructed
     * @throws java.net.UnknownHostException If Host can't be Identified
     */
    public TFTPUDPClient() throws SocketException, UnknownHostException
    {
        Random rn = new Random();
        // generates TID values between 1025 and 65,535
        // because ports below 1025 require administrative rights
        int port = rn.nextInt((65535 - 1025) + 1) + 1025;
        socket = new DatagramSocket(port);
        IPAddress = InetAddress.getByName("127.0.0.1");
    }
    
    /**
     * Creates a Menu
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
                     filename = fileInput.nextLine();
                     sendRequest(WRQ,filename,"octet");
                     receiveACK();
                     sendToServer(); // start sending file data to server
                     break;
                     
                 case 2:
                     System.out.print("Enter file name to retrieve:");
                     filename = fileInput.nextLine();
                     sendRequest(RRQ,filename,"octet");
                     writeToFile(); // start reading file data sent from server
                     break;
                     
                 case 3:
                     socket.close();
                     System.exit(0);
             }
        }
    }
    
    /**
     * Sends a Read/Write Packet
     * @param opCode The opCode is 01
     * @param filename The desired filename set by the user
     * @param mode The mode is octet
     * @throws java.io.UnsupportedEncodingException If UTF-8 is not supported
     */
    public void sendRequest(byte[] opCode, String filename, String mode) throws UnsupportedEncodingException, IOException
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        
        os.write(opCode, 0, opCode.length);
        os.write(filename.getBytes("UTF-8"), 0, filename.getBytes().length);
        os.write(SEPARATOR);
        os.write(mode.getBytes(),0,mode.getBytes().length);
        os.write(SEPARATOR);        
        
        byte[] requestPacket = os.toByteArray();
        
        packet = new DatagramPacket(requestPacket,requestPacket.length, IPAddress, TFTP_PORT);
        socket.send(packet);
        socket.setSoTimeout(10000);  // 10 second timer 
    }
    
    
    /**
     * Reads 512 Bytes of Data from File at a time
     * Sends File Data to Server
     * @throws java.io.IOException
     */
    public void sendToServer() throws IOException
    {
        try
        {
            // create the input stream to read file data
            FileInputStream fis = new FileInputStream(filename);

            // used to write a byte of file data
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            
            // start reading
            int byteRead;
            int totalBytesRead = 0;
        
            while ((byteRead = fis.read()) != -1)
            {
                bos.write(byteRead);
                totalBytesRead++;
            
                if (totalBytesRead == 512)
                {
                    byte[] fileData = bos.toByteArray(); // convert file data to byte array
                    incrementBlockNumber();
                    sendDataPacket(DATA,blockNumber,fileData);
                    receiveACK();
                    // carry on reading
                    totalBytesRead = 0; // reset bytes read
                    bos.reset(); // reset output stream  
                }
            }
        
            // total bytes read was less than 512, It's the last data packet!
            // send data packet
            byte[] fileData = bos.toByteArray();
            incrementBlockNumber();
            sendDataPacket(DATA,blockNumber,fileData);
        
            // wait for last ACK
            // close streams
            receiveACK();
            fis.close();
            bos.close();
            
            System.out.println("The file " + filename + " has been transferred");
            
        }
        catch (FileNotFoundException ex)
        {
            System.out.println("File Not Found In Client!");
        }
    }
    
    /**
     * Receives ACK Packets from Server
     * On Timeout Retransmits Last Sent Data Packet
     * Stops Retransmitting when Max number of retries reached
     * @throws IOException
     */
    public void receiveACK() throws IOException
    {
        boolean received = false;
        
        while (!received)
        {
            try
            {
                byte[] receiveData = new byte[4];
                receivedPacket = new DatagramPacket(receiveData,receiveData.length);
                socket.receive(receivedPacket);
                received = true;
            }
            catch (SocketTimeoutException e)
            {
                if (retries != MAX_RETRIES)
                {
                    System.out.println("The Socket Timed Out!");
                    System.out.println("Retransmitting Data Packet...");
                    socket.send(packet);
                    socket.setSoTimeout(10000);
                    retries++;
                }
            }
        }
    }
        
    /**
     * Sends a TFTP Data Packet
     * @param opCode opCode is 03
     * @param blockNo the current block number
     * @param data the file data
     * @throws java.io.IOException
     */
    public void sendDataPacket(byte[] opCode, byte[] blockNo, byte[] data) throws IOException
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        
        os.write(opCode, 0, opCode.length);
        os.write(blockNo, 0, blockNo.length);
        os.write(data, 0, data.length);
        
        byte[] dataPacket = os.toByteArray();
        
        packet = new DatagramPacket(dataPacket,dataPacket.length, IPAddress, TFTP_PORT);
        socket.send(packet);
        socket.setSoTimeout(10000);    
    }
    
    /**
     * Increments Block Number 
     * Handles rounding of 2nd byte when it reaches 9
     */
    public void incrementBlockNumber()
    {
        if (blockNumber[1] == 9)
        {
            blockNumber[0]++;
            blockNumber[1] = 0;
        }
        else
        {
            blockNumber[1]++;
        }
    }
    
    /**
     * Receives Data from Server in 512 Byte Chunks
     * Writes Data To A File
     * Sends ACK's To Server
     * On Timeout - Retransmits ACK
     * @throws java.io.IOException
     */
    public void writeToFile() throws IOException
    {   
        
        boolean finishedReceiving = false;
        boolean fileCreated = false;
        
        while (!finishedReceiving)
        {
            try
            {
                // maximum size of received packet
                byte[] receiveData = new byte[516]; 
                // Receive The Packet
                receivedPacket = new DatagramPacket(receiveData,receiveData.length);
                socket.receive(receivedPacket);
                
                // Extract Opcode
                ByteArrayInputStream bis = new ByteArrayInputStream(receivedPacket.getData());
                
                // Extract opcode
                byte[] opcode = new byte[2];
                bis.read(opcode,0,2);
                
                // Client Has Received An Error Packet - File Not Found on Server
                if (Arrays.equals(opcode,ERROR))
                {
                    bis.close();
                    finishedReceiving = true;
                    System.out.println("File Not Found on Server!");
                }
                
                if (Arrays.equals(opcode, DATA))
                {
                    // If File has not been created already
                    if (fileCreated == false)
                    {
                        // Create File To Write Data To
                        fos = new FileOutputStream(filename);
                        fileCreated = true;
                    }
                    
                    // Extract Block Number of Data Packet
                    bis.read(blockNumber,0, 2);
                
                    // Start Writing File Data
                    int byteRead;
                    int totalBytesRead = 0;
                
                    // while we haven't reached max file size of 512 bytes
                    while ((byteRead = bis.read()) != -1) 
                    {
                        if (byteRead != 0)
                        {
                            fos.write(byteRead);
                            totalBytesRead++;      
                        }
                        else
                        {
                            // When file data is < 512 bytes
                            // and we've reached the end of the data (byteRead == 0)
                            // but it's less than the max size of 512 bytes
                            // stop reading remaining 0 bytes
                            break;
                        }
                    }
                
                    if (totalBytesRead == 512)
                    {
                        sendACK(ACK,blockNumber); 
                    }
                
                    // If This Was Last Data Packet Sent?
                    if (totalBytesRead < 512)
                    {
                        sendLastACK(ACK,blockNumber);
                        bis.close();
                        fos.close();
                        finishedReceiving = true;
                        System.out.println("The file " + filename + " has been stored");
                    }
                }
            }
            catch (SocketTimeoutException e)
            {
                if (retries != MAX_RETRIES)
                {
                    System.out.println("Socket Timed Out!");
                    System.out.println("Retransmitting Packet...");
                    socket.send(packet);
                    socket.setSoTimeout(10000);
                    retries++;
                }
            }
        }
    }
    
    
    /**
     * Sends a TFTP ACK Packet
     * @param opCode opCode is 04
     * @param blockNo block number of received data packet
     * @throws java.io.IOException
     */
    public void sendACK(byte[] opCode, byte[] blockNo) throws IOException
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        
        os.write(opCode, 0, opCode.length);
        os.write(blockNo, 0, blockNo.length);
        
        byte[] ACKPacket = os.toByteArray();
        packet = new DatagramPacket(ACKPacket, ACKPacket.length,IPAddress,TFTP_PORT);
        socket.send(packet);
        socket.setSoTimeout(10000);

    }
    
    /**
     * Sends The Last TFTP ACK Packet - No Timer
     * @param opCode opCode is 04
     * @param blockNo block number of received data packet
     * @throws java.io.IOException
     */
    public void sendLastACK(byte[] opCode, byte[] blockNo) throws IOException
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write(opCode, 0, opCode.length);
        os.write(blockNo, 0, blockNo.length);
        
        byte[] ACKPacket = os.toByteArray();
        packet = new DatagramPacket(ACKPacket, ACKPacket.length,IPAddress,TFTP_PORT);
        socket.send(packet);
        socket.setSoTimeout(10000);  
    }
    
    public static void main(String[] args) throws IOException
    {
        new TFTPUDPClient().run(); 
    }   
}