/*
 * Author: Jonatan Schroeder
 * Updated: March 2022
 *
 * This code may not be used without written consent of the authors.
 */

package ca.yorku.rtsp.client.net;

import ca.yorku.rtsp.client.exception.RTSPException;
import ca.yorku.rtsp.client.model.Frame;
import ca.yorku.rtsp.client.model.Session;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket; // added for RTP connection
import java.net.Socket;
import java.nio.Buffer;
import java.sql.Connection;


/**
 * This class represents a connection with an RTSP server.
 */
public class RTSPConnection {

    private static final int BUFFER_LENGTH = 0x10000;

    private Session session;

    public Socket socket;

    public BufferedReader in;

    public PrintWriter output;

    public int seqNum = 0;

    public int sessionNum;

    public DatagramPacket DGpacket;

    public String vidName;   // necessary for play(),

    public DatagramSocket DGsocket;

    public int DGsocketPort;

    // TODO Add additional fields, if necessary

    /**
     * Establishes a new connection with an RTSP server. No message is sent at this point, and no stream is set up.
     *
     * @param session The Session object to be used for connectivity with the UI.
     * @param server  The hostname or IP address of the server.
     * @param port    The TCP port number where the server is listening to.
     * @throws RTSPException If the connection couldn't be accepted, such as if the host name or port number are invalid
     *                       or there is no connectivity.
     */
    public RTSPConnection(Session session, String server, int port) throws RTSPException {
        this.session = session;
        try {
            socket = new Socket(server, port);
        } catch (Exception e) {
            throw new RTSPException("Invalid server/port!");

        }
    }

    /**
     * Sends a SETUP request to the server. This method is responsible for sending the SETUP request, receiving the
     * response and retrieving the session identification to be used in future messages. It is also responsible for
     * establishing an RTP datagram socket to be used for data transmission by the server. The datagram socket should be
     * created with a random UDP port number, and the port number used in that connection has to be sent to the RTSP
     * server for setup. This datagram socket should also be defined to timeout after 1 second if no packet is
     * received.
     *
     * @param videoName The name of the video to be setup.
     * @throws RTSPException If there was an error sending or receiving the RTSP data, or if the RTP socket could not be
     *                       created, or if the server did not return a successful response.
     */
    public synchronized void setup(String videoName) throws RTSPException {
        try {
            DGsocket = new DatagramSocket();
            DGsocket.setSoTimeout(1000);
        } catch (Exception e) {
            throw new RTSPException("TIME OUT ERROR!!");
        }
        DGsocketPort = DGsocket.getLocalPort();
        vidName = videoName;
        seqNum++;   // increment seqNum, keeping track per req.
        String req = "SETUP " + vidName + " RTSP/1.0\nCSeq: " + seqNum + "\nTransport:RTP/UDP; client_port= " + DGsocketPort + "\n";

        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            output.write(req + '\n');
            output.flush();
            RTSPResponse serverResponse = readRTSPResponse();
            if (serverResponse.getResponseCode() != 200) throw new RTSPException("Response code invalid");
            sessionNum = Integer.parseInt(serverResponse.getHeaderValue("session"));
        } catch (RTSPException e) {
            throw new RTSPException("Error!");
        } catch (Exception e) {
            throw new RTSPException("Error setup!");
        }


    }

    /**
     * Sends a PLAY request to the server. This method is responsible for sending the request, receiving the response
     * and, in case of a successful response, starting a separate thread responsible for receiving RTP packets with
     * frames.
     *
     * @throws RTSPException If there was an error sending or receiving the RTSP data, or if the server did not return a
     *                       successful response.
     */
    public synchronized void play() throws RTSPException {
        seqNum++;   // increment seqNum, keeping track per req.
        String req = "PLAY " + vidName + " RTSP/1.0\nCSeq: " + seqNum + "\nSession: " + sessionNum + "\n";
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            output.write(req + '\n');
            output.flush();
            RTSPResponse serverResponse = readRTSPResponse();
            if (serverResponse.getResponseCode() != 200) throw new RTSPException("Response code invalid");

            new RTPReceivingThread().start();   // "start()" a method from Thread class. Refer to JavaDocs.
        } catch (RTSPException e) {
            throw new RTSPException("Error!");
        } catch (Exception e) {
            throw new RTSPException("ERROR");
        }

    }

    private class RTPReceivingThread extends Thread {
        /**
         * Continuously receives RTP packets until the thread is cancelled. Each packet received from the datagram
         * socket is assumed to be no larger than BUFFER_LENGTH bytes. This data is then parsed into a Frame object
         * (using the parseRTPPacket method) and the method session.processReceivedFrame is called with the resulting
         * packet. The receiving process should be configured to timeout if no RTP packet is received after two seconds.
         */
        @Override
        public void run() {
            byte[] buff = new byte[BUFFER_LENGTH];
            DGpacket = new DatagramPacket(buff,buff.length);
            try{
                while (true) {
                    DGsocket.receive(DGpacket);
                    DGsocket.setSoTimeout(2000);    // if 2 seconds pass and it doesn't receive anything, drop packet.
                    session.processReceivedFrame(parseRTPPacket(DGpacket));
                }
            }
            catch(Exception e){
                System.out.println("RUN ERROR!");
            }
        }

    }

    /**
     * Sends a PAUSE request to the server. This method is responsible for sending the request, receiving the response
     * and, in case of a successful response, stopping the thread responsible for receiving RTP packets with frames.
     *
     * @throws RTSPException If there was an error sending or receiving the RTSP data, or if the server did not return a
     *                       successful response.
     */
    public synchronized void pause() throws RTSPException {
        seqNum++;   // increment seqNum, keeping track per req.
        String req = "PAUSE " + vidName + " RTSP/1.0\nCSeq: " + seqNum + "\nSession: " + sessionNum + "\n";
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            output.write(req + '\n');
            output.flush();
            RTSPResponse serverResponse = readRTSPResponse();
            if (serverResponse.getResponseCode() != 200) throw new RTSPException("Response code invalid");
            new RTPReceivingThread().interrupt();
        } catch (RTSPException e) {
            throw new RTSPException("Error!");
        } catch (Exception e) {
            throw new RTSPException("Error Pause!!");
        }
    }

    /**
     * Sends a TEARDOWN request to the server. This method is responsible for sending the request, receiving the
     * response and, in case of a successful response, closing the RTP socket. This method does not close the RTSP
     * connection, and a further SETUP in the same connection should be accepted. Also this method can be called both
     * for a paused and for a playing stream, so the thread responsible for receiving RTP packets will also be
     * cancelled.
     *
     * @throws RTSPException If there was an error sending or receiving the RTSP data, or if the server did not return a
     *                       successful response.
     */
    public synchronized void teardown() throws RTSPException {
        seqNum++;   // increment seqNum, keeping track per req.
        String req = "TEARDOWN " + vidName + " RTSP/1.0\nCSeq: " + seqNum + "\nSession: " + sessionNum + "\n";
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            output.write(req + '\n');
            output.flush();
            RTSPResponse serverResponse = readRTSPResponse();
            if (serverResponse.getResponseCode() != 200) throw new RTSPException("Response code invalid");
            new RTPReceivingThread().interrupt(); // stops thread
            DGsocket.close();   // closes socket after receiving string request, and checking response code
        } catch (RTSPException e) {
            throw new RTSPException("Error!");
        } catch (Exception e) {
            throw new RTSPException("Error teardown!");
        }
    }

    /**
     * Closes the connection with the RTSP server. This method should also close any open resource associated to this
     * connection, such as the RTP connection, if it is still open.
     */
    public synchronized void closeConnection() {
        try {
            socket.close();
        } catch (Exception e) {
            // nothing
        }
    }

    /**
     * Parses an RTP packet into a Frame object.
     *
     * @param packet the byte representation of a frame, corresponding to the RTP packet.
     * @return A Frame object.
     */
    public static Frame parseRTPPacket(DatagramPacket packet) {
        byte[] array = packet.getData(); // temp array to grab each var
        byte payloadType = (byte)(array[1]&0x7F);
        boolean marker = ((array[1] & 0xff) & 0x80) == 0x80;
        short frameSeqNum = (short) ((array[3] & 0xff) | (array[2]<<8));
        int timestamp = (array[4] & 0xff) << 24 | (array[5] & 0xff) << 16 | (array[6] & 0xff) << 8 | (array[7] & 0xff);
        byte[] payload = packet.getData();          // payload, offset, and length methods from DatagramPacket javadoc.
        int offset = 12;
        int length = packet.getLength() - offset;
        Frame frame = new Frame(payloadType, marker, frameSeqNum, timestamp, payload, offset,length);
        return frame;
    }

    /**
     * Reads and parses an RTSP response from the socket's input.
     *
     * @return An RTSPResponse object if the response was read completely, or null if the end of the stream was reached.
     * @throws IOException   In case of an I/O error, such as loss of connectivity.
     * @throws RTSPException If the response doesn't match the expected format.
     */
    public RTSPResponse readRTSPResponse() throws IOException, RTSPException {

        BufferedReader respReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String verCodeMsg = respReader.readLine();
        if (verCodeMsg == null) return null;    // returns nothing if nothing received from input stream

        String[] verCodeMsgSplit = verCodeMsg.split(" ", 3);

        RTSPResponse response = new RTSPResponse(verCodeMsgSplit[0], Integer.parseInt(verCodeMsgSplit[1]), verCodeMsgSplit[2]);

        /** Second half of method iterates through response code, looking for either the CSeq or Session value, and
         *  adds said value to the header by iterating through the input stream.
         */
        String respNext;
        int cSeq = 0;
        int session = 0;
        String cSeqStr = "";
        String sessionStr = "";
        while (!(respNext = respReader.readLine()).equals("")) {

            String[] respNextSplit = respNext.split(":", 2);

            if (respNextSplit[0].equalsIgnoreCase("CSeq")) {
                cSeq = Integer.parseInt(respNextSplit[1].trim());
                cSeqStr = String.valueOf(cSeq);
            }

            if (respNextSplit[0].equalsIgnoreCase("Session")) {
                session = Integer.parseInt(respNextSplit[1].trim());
                sessionStr = String.valueOf(session);
            }
            response.addHeaderValue("Session", sessionStr);
            response.addHeaderValue("CSeq", cSeqStr);


        }
        return response;

    }
}
