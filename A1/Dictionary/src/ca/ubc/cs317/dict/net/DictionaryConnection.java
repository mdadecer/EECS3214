package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import javax.xml.crypto.Data;

import static ca.ubc.cs317.dict.net.Status.readStatus;


import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;


public class DictionaryConnection {
    
    private static final int DEFAULT_PORT = 2628;

    public Socket socket;
    public BufferedReader in;
    public PrintWriter out; 

    /**
     * Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     *                                 don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {
        Status stat;
        try {
            socket = new Socket(host, port); // create a connection between the host and port of the server
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            stat = Status.readStatus(in);
            // checks if status code is valid, if not throw DictConnectionException
            if ((stat.getStatusCode() != 220)) {
                throw new DictConnectionException(stat.getDetails());
            }
//            System.out.println("connection has been established");

        } catch (Exception e) { // if host/port invalid, throw DictConnExcep
            throw new DictConnectionException();
        }
    }


    /**
     * Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     *                                 don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException, IOException {
        this(host, DEFAULT_PORT);
        Status stat;
        try {
            socket = new Socket(host, DEFAULT_PORT); // create a connection between the host and port of the server
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            stat = Status.readStatus(in);
            // checks if status code is valid, if not throw DictConnectionException
            if ((stat.getStatusCode() != 220)) {
                throw new DictConnectionException(stat.getDetails());
            }

        } catch (Exception e) { // if host/port invalid, throw DictConnExcep
            throw new DictConnectionException();
        }


    }

    /**
     * Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     */
    public synchronized void close() {

        // sends 'quit' command to server, and closes socket; ending connection between two.
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println("quit");
            String closeMessage = in.readLine();
            System.out.println(closeMessage);
            socket.close();
        } catch (Exception e) {
            // ignores any/all exceptions happening when sending message
        }

    }




    /**
     * Requests and retrieves a map of database name to an equivalent database object for all valid databases used in the server.
     *
     * @return A map linking database names to Database objects for all databases supported by the server, or an empty map
     * if no databases are available.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Map<String, Database> getDatabaseList() throws DictConnectionException {
        Map<String, Database> databaseMap = new HashMap<>();
        
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println("SHOW DB");
            Status stat;
            try {  
                stat = Status.readStatus(in); 
                // Check status code, depending on what code we get, return set/throw exception
                if (stat.getStatusCode() != 110) {  
                    if (stat.getStatusCode() == 554) { // if code is 554, returns empty map
                        return databaseMap;
                    }
                    throw new DictConnectionException(); // throw exception if code isn't 554 or 110
                } 
                String[] dbList = DictStringParser.splitAtoms(stat.getDetails());
                int numOfdbs = Integer.parseInt(dbList[0]); 
                int i = 0;

                while (i < numOfdbs) { // separate in.readline() into categories for database
                    String[] line = DictStringParser.splitAtoms(in.readLine());
                    Database db = new Database(line[0], line[1]);
                    databaseMap.put(line[0],db); // add to DBmap, which'll be returned
                    i++;
                }

            }
            catch (Exception e) {throw new DictConnectionException();}
                in.readLine();

        }
        catch(Exception e) {throw new DictConnectionException();}
        return databaseMap;
    }



    /**
     * Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server, or an empty set if no strategies are supported.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();
        
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println("SHOW STRAT");
            Status stat;
            try {
                stat = Status.readStatus(in);
                if (stat.getStatusCode() != 111){
                    if (stat.getStatusCode() == 555){
                        return set;
                    }
                    throw new DictConnectionException();
                } 
                String[] stratList = DictStringParser.splitAtoms(stat.getDetails());
                int numOfStrats = Integer.parseInt(stratList[0]);
                int i = 0;

                while (i < numOfStrats) {
                    String[] line = DictStringParser.splitAtoms(in.readLine());
                    MatchingStrategy strategy = new MatchingStrategy(line[0], line[1]);
                    set.add(strategy);
                    i++;
                }
            }
            catch (Exception ignored) {}

            in.readLine();

        }
        catch (Exception e) {throw new DictConnectionException();}

        return set;
    }


    /**
     * Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server, or an empty set if no matches were found.
     * @throws DictConnectionException If the connection was interrupted, the messages don't match their expected
     *                                 value, or the database or strategy are invalid.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();

        String msg = "MATCH " + database.getName() + " " + strategy.getName() + " " + "\"" + word + "\"";

        // check if input has 2 words
        if (word.contains(" ")&& !(word.startsWith("\"")) && (word.endsWith("\""))){
            msg += ('"' + word + '"');
        }
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println(msg);
            Status stat;  

            try {
                stat = Status.readStatus(in);
                if (stat.getStatusCode() != 152)
                {
                    if (stat.getStatusCode() == 552){ // if no words found
                        return set;
                    } 
                    else { throw new DictConnectionException(); } // if not 152 or 552 throw new exception

                }

                
                String[] matchList = DictStringParser.splitAtoms(stat.getDetails());
                int numOfMatches = Integer.parseInt(matchList[0]);
                int i = 0;

                while (i < numOfMatches) { // separate in.readline() into categories for set
                    String[] line = DictStringParser.splitAtoms(in.readLine());
                    set.add(line[1]);
                    i++;
                }

            }
            catch (Exception e) {throw new DictConnectionException();}

            in.readLine();

        }
        catch (Exception e) {throw new DictConnectionException();}

        return set;
    }


    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server, or an empty
     * collection if no definitions were returned.
     * @throws DictConnectionException If the connection was interrupted, the messages don't match their expected
     * value, or the database is invalid.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();
        String msg = "DEFINE " + database.getName() + " "  + "\"" + word + "\"";

        // check if input has 2 words
        if (word.contains(" ")&& !(word.startsWith("\"")) && (word.endsWith("\""))){
            msg += ('"' + word + '"');
        }
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println(msg);
            Status stat;

            try {
                String dbName = database.getName();
                stat = Status.readStatus(in);
                if (stat.getStatusCode() != 150){
                    if (stat.getStatusCode() == 552){
                        return set;
                    }
                    throw new DictConnectionException();
                }

                String[] defList = DictStringParser.splitAtoms(stat.getDetails());
                int numOfDefs = Integer.parseInt(defList[0]);
                int i = 0;

                while (i < numOfDefs) {
                    if (readStatus(in).getStatusCode() != 151) {
                        throw new DictConnectionException();
                    }
                    String[] line = DictStringParser.splitAtoms(in.readLine());
                    Definition def = new Definition(line[0], line[1]);
                    def.appendDefinition(in.readLine());
                    set.add(def);
                    i++;
                }
            }
            catch (Exception e) {throw new DictConnectionException();}

            in.readLine();

        }
        catch (Exception e) {throw new DictConnectionException();}

        return set;
    }

}
