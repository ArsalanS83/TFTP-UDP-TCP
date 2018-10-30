package tftp.udp.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;

/**
 * TFTP Server Built on UDP
 * @author 105977
 */
public class TFTPUDPServer extends Thread {
    
    protected DatagramSocket socket;
    protected DatagramPacket receivedPacket;
    protected DatagramPacket packet;
    
    protected String filename; // filename requested by client
    protected int clientPort;
    protected InetAddress clientIP;
    
    // TFTP opcodes
    protected static final byte RRQ [] = new byte[] {0,1};
    protected static final byte WRQ [] = new byte[] {0,2};
    protected static final byte ACK [] = new byte[] {0,4};
    protected static final byte DATA [] = new byte[] {0,3};
    protected static final byte ERROR [] = new byte[] {0,5};
    
    // File Not Found Error Code
    protected static final byte ERROR_CODE [] = new byte[] {0,1};
    protected static final String ERROR_MSG = "File not found";
    protected static byte SEPARATOR = 0;
    
    protected byte[] blockNumber = new byte[2];
    
    protected ByteArrayInputStream bis; // used for reading packet data
    protected FileOutputStream fos; // used to write data to file
    protected FileInputStream fis; // used to read file data
    protected ByteArrayOutputStream bos; // used to write file data
    
    protected static int MAX_RETRIES = 10; // max number of retransmits on socket timeout
    protected int retries; // current number of retransmit attempts
    
    
    /**
     * Constructs TFTP UDP Client
     * Creates a Socket
     * @throws SocketException If Socket can't be constructed
     */
    public TFTPUDPServer() throws SocketException
    {
        socket = new DatagramSocket(9000);
    }
    
    
    @Override
    public void run()
    {
        // maximum size of received packet
        byte[] receiveData = new byte[516]; 
        
        while (true)
        {
            try
            {
                // Receive The Packet
                receivedPacket = new DatagramPacket(receiveData,receiveData.length);
                socket.receive(receivedPacket);
                
                // Extract Opcode
                bis = new ByteArrayInputStream(receivedPacket.getData());
                byte[] opcode = new byte[2];
                bis.read(opcode,0,2);
                
                // Extract Client Port and IP Address
                clientPort = receivedPacket.getPort();
                clientIP = receivedPacket.getAddress();
                
                // If Client Sent WRQ
                if (Arrays.equals(opcode,WRQ))
                {
                    // Initialise Block Number
                    blockNumber[0] = 0;
                    blockNumber[1] = 0;
                    
                    extractFileName();
                    
                    sendACK(ACK,blockNumber);
                    
                    fos = new FileOutputStream(filename);
                }
                
                
                // If Client Sent DATA
                if (Arrays.equals(opcode,DATA))
                {
                    // Read Block Number of Data Packet
                    bis.read(blockNumber,0, 2);
                    
                    // Write File Data
                    int byteRead;
                    int totalBytesRead = 0;
                    
                    while ((byteRead = bis.read()) != -1)
                    {
                        if (byteRead != 0)
                        {
                            fos.write(byteRead);
                            totalBytesRead++;      
                        }
                        else
                        {
                            break;
                        }
                    }
                    
                    if (totalBytesRead == 512)
                    {
                        sendACK(ACK,blockNumber); 
                    }
                    
                    // If This Was Last Data Sent?
                    if (totalBytesRead < 512)
                    {
                        sendLastACK(ACK,blockNumber);
                        bis.close();
                        fos.close();
                    }
                }
                
                
                // If Client Sent RRQ
                if (Arrays.equals(opcode, RRQ))
                {
                    blockNumber[0] = 0;
                    blockNumber[1] = 0;
                    
                    extractFileName();
                    
                    try
                    {
                        // Used To Read File Data
                        fis = new FileInputStream(filename);
                    
                        // Used To Write File Data
                        bos = new ByteArrayOutputStream();
                    
                        // start reading file data
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
                            
                                // wait for ACK from Client
                                boolean received = false;
                            
                                while (!received)
                                {
                                    try
                                    {
                                        byte[] ACKPacket = new byte[4];
                                        receivedPacket = new DatagramPacket(ACKPacket,ACKPacket.length);
                                        socket.receive(receivedPacket);
                                        received = true;
                                    }
                                    catch (SocketTimeoutException e)
                                    {
                                        if (retries != MAX_RETRIES)
                                        {
                                            System.out.println("Socket Timed Out!");
                                            System.out.println("Retransmitting Packet...");
                                            socket.send(packet);
                                            socket.setSoTimeout(10000); // 10 second timer
                                            retries++;
                                        }
                                    }
                                }                          
                                // carry on reading
                                totalBytesRead = 0; // reset bytes read
                                bos.reset(); // reset output stream 
                            }
                        }
                        
                        if (totalBytesRead < 512)
                        {
                            // total bytes read was less than 512, It's the last data packet!
                            // send data packet
                            byte[] fileData = bos.toByteArray();
                            incrementBlockNumber();
                            sendDataPacket(DATA,blockNumber,fileData);
                            
                            // wait for last ACK
                            // close streams
                            boolean received = false;
                            while (!received)
                            {
                                try
                                {
                                    byte[] ACKPacket = new byte[4];
                                    receivedPacket = new DatagramPacket(ACKPacket,ACKPacket.length);
                                    socket.receive(receivedPacket);
                                    received = true;
                                    fis.close();
                                    bos.close();
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
                    }
                    catch (FileNotFoundException e)
                    {
                        sendErrorPacket(ERROR,ERROR_CODE,ERROR_MSG);
                    }
                }
            }
            catch (IOException e)
            {

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
        packet = new DatagramPacket(ACKPacket, ACKPacket.length,clientIP,clientPort);
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
        packet = new DatagramPacket(ACKPacket, ACKPacket.length,clientIP,clientPort);
        socket.send(packet);
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
         while ((fileNameByte = bis.read()) != 0)
         {
             fileNameBytes.write(fileNameByte);
         }

        // store filename bytes into filename string
        filename = fileNameBytes.toString();
        
        // finished extracting opcode and filename
        bis.close();
        fileNameBytes.close();       
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
        
        packet = new DatagramPacket(dataPacket,dataPacket.length, clientIP, clientPort);
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
     * Receives ACK Packets from Server
     * On Timeout Retransmits Last Sent Data Packet
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
     * Sends a TFTP Error Packet
     * @param opCode opCode is 05
     * @param errorCode the errorCode of the packet
     * @param errorMsg the error message of the packet
     * @throws java.io.IOException
     */
    public void sendErrorPacket(byte[] opCode, byte[] errorCode, String errorMsg) throws IOException
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        
        os.write(opCode, 0, opCode.length);
        os.write(errorCode, 0, errorCode.length);
        os.write(errorMsg.getBytes("UTF-8"), 0, errorMsg.length());
        os.write(SEPARATOR);
        
        byte[] errorPacket = os.toByteArray();
        
        packet = new DatagramPacket(errorPacket,errorPacket.length, clientIP, clientPort);
        socket.send(packet);
    }
    
    
    public static void main(String[] args) throws IOException
    {
        new TFTPUDPServer().start();
        System.out.println("TFTP Server Started"); 
    }
}