package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import static ca.ubc.cs317.dict.net.Status.readStatus;


import java.io.*;
import java.net.Socket;
import java.util.*;

public class DictionaryConnection3 {
    public Socket socket;
    public BufferedReader in;
    public PrintWriter output;
    private static final int DEFAULT_PORT = 2628;

    /**
     * Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     *                                 don't match their expected value.
     */
    public DictionaryConnection3(String host, int port) throws DictConnectionException {
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Status status = Status.readStatus(in);
            // checks if status code is valid, if not throw DictConnectionException
            if ((status.getStatusCode() != 220)) {
                throw new DictConnectionException(status.getDetails());
            }
            System.out.println("connection established!");
        } catch (Exception e) {
            e.printStackTrace();
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
    public DictionaryConnection3(String host) throws DictConnectionException, IOException {
        this(host, DEFAULT_PORT);
    }

    /**
     * Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     */
    public synchronized void close() {
        try {
            output = new PrintWriter(socket.getOutputStream(), true);
            output.println("quit");
            String closeMessage = in.readLine();
            System.out.println(closeMessage);
            socket.close();
        } catch (Exception e) {
            // nothing
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
            output = new PrintWriter(socket.getOutputStream(), true);
            output.println("SHOW DB");
            Status status;
            try {
                status = Status.readStatus(in);
                if (status.getStatusCode() != 110) {
                    if (status.getStatusCode() == 554) {
                        return databaseMap;
                    }
                    throw new DictConnectionException();
                }
                String[] dbList = DictStringParser.splitAtoms(status.getDetails());
                int numOfDbs = Integer.parseInt(dbList[0]);
                int i = 0;
                while (i < numOfDbs) {
                    String[] lineList = DictStringParser.splitAtoms(in.readLine());
                    Database database = new Database(lineList[0], lineList[1]);
                    databaseMap.put(lineList[0], database);
                    i++;
                }
//            if (readStatus(in).getStatusCode() != 250) throw new DictConnectionException();
            } catch (Exception e) {
                throw new DictConnectionException();
            }
            in.readLine();

        } catch (Exception e) {
            throw new DictConnectionException();
        }

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
            output = new PrintWriter(socket.getOutputStream(), true);
            output.println("SHOW STRAT");
            Status status;

            try {
                status = Status.readStatus(in);
                if (status.getStatusCode() != 111) {
                    if (status.getStatusCode() == 555) {
                        return set;
                    }
                    throw new DictConnectionException();
                }
                String[] stratList = DictStringParser.splitAtoms(status.getDetails());
                int numOfStrats = Integer.parseInt(stratList[0]);
                int i = 0;

                while (i < numOfStrats) {
                    String[] line = DictStringParser.splitAtoms(in.readLine());
                    MatchingStrategy strategy = new MatchingStrategy(line[0], line[1]);
                    set.add(strategy);
                    i++;
                }
            } catch (Exception e) {
                throw new DictConnectionException();
            }

            in.readLine();

        } catch (Exception e) {
            throw new DictConnectionException();
        }

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
        if (word.contains(" ") && !(word.startsWith("\"")) && (word.endsWith("\""))) {
            msg += ('"' + word + '"');
        }
        try {
            output = new PrintWriter(socket.getOutputStream(), true);
            output.println(msg);
            Status status;

            try {
                status = Status.readStatus(in);
                if (status.getStatusCode() != 152) {
                    if (status.getStatusCode() == 552) { // if no words found
                        return set;
                    } else {
                        throw new DictConnectionException();
                    } // if 550 or 551

                }


                String[] matchList = DictStringParser.splitAtoms(status.getDetails());
                int numOfMatches = Integer.parseInt(matchList[0]);
                int i = 0;

                while (i < numOfMatches) {
                    String[] line = DictStringParser.splitAtoms(in.readLine());
                    set.add(line[1]);
                    i++;
                }

            } catch (Exception e) {
                throw new DictConnectionException();
            }

            in.readLine();

        } catch (Exception e) {
            throw new DictConnectionException();
        }

        return set;
    }


    /**
     * Requests and retrieves all definitions for a specific word.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server, or an empty
     * collection if no definitions were returned.
     * @throws DictConnectionException If the connection was interrupted, the messages don't match their expected
     *                                 value, or the database is invalid.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();
        String msg = "DEFINE " + database.getName() + " "  + "\"" + word + "\"";

        // check if input has 2 words
        if (word.contains(" ")&& !(word.startsWith("\"")) && (word.endsWith("\""))){
            msg += ('"' + word + '"');
        }
        try {
            output = new PrintWriter(socket.getOutputStream(), true);
            output.println(msg);
            Status status;

            try {
                status = Status.readStatus(in);
                if (status.getStatusCode() != 150){
                    if (status.getStatusCode() == 552){
                        return set;
                    }
                    throw new DictConnectionException();
                }
                String[] defList = DictStringParser.splitAtoms(status.getDetails());
                int numOfDefs = Integer.parseInt(defList[0]);
                int i = 0;
                while (i < numOfDefs) {
                    if (readStatus(in).getStatusCode() != 151){
                        throw new DictConnectionException();
                    }
                    String[] line = DictStringParser.splitAtoms(in.readLine());
                    Definition def = new Definition(line[0], line[1]);
                    String readLine = in.readLine();
                    while(!readLine.startsWith("."))
                    {
                        def.appendDefinition(readLine);
                    }
                    set.add(def);
                    i++;
                }
            }
            catch (Exception e)
            {
                throw new DictConnectionException();
            }

        }
        catch (Exception e) {throw new DictConnectionException();}

        return set;
    }

}
