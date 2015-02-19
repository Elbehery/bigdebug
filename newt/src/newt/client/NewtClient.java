package newt.client;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Vector;

import org.apache.log4j.Logger;

import newt.actor.FileLocatable;
import newt.actor.ProvenanceContainer;
import newt.actor.ProvenanceDataType;
import newt.actor.ProvenanceItem;
import newt.actor.ProvenanceTask;
import newt.common.ByteArray;
import newt.common.RpcCommunication;
import newt.contract.NewtService;
import newt.utilities.*;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class NewtClient<I extends ProvenanceDataType, O extends ProvenanceDataType> implements java.io.Serializable{
    public enum Mode {
        CAPTURE,
        REPLAY,
        NONE
    }
    
    Mode                    clientMode = Mode.NONE;
    public static String    DEFAULT_MASTER_IP = "131.179.49.1";//"192.168.1.76";//"csag105.sysnet.ucsd.edu"; //"csag101.sysnet.ucsd.edu";
    private static String   masterUrl = "http://" + DEFAULT_MASTER_IP + ":8899/hessianrpc";
    NewtService             masterClient = null;
    NewtService             peerClient = null;
    final int               maxProv = 5000;

    String                  universeName = null;
    int                     universeID = -1;
    String                  actorName = null;
    String                  actorType = null;
    String                  relativeID = null;

    int                     actorID = -1;
    int                     parentID = -1;
    int                     schemaID = -1;
    String                  tableName = null;
    int                     maxParallelRequests = 1;
    NewtProvenanceSender    newtProvenanceSender = null;
    ProvenanceContainer     provenanceContainer = null;
    ArrayList               provenance = null;
    boolean					isSubActor = false;
    

    int                     traceID = -1;
    HashSet                 replayFilter = null;

    boolean                 isInputFileLocatableSet = false;
    boolean                 isOutputFileLocatableSet = false;
    boolean                 inputFileLocatable = false;
    boolean                 outputFileLocatable = false;
    HashSet<String>          fileLocatables = null;
    int                     count = 0;
    int                     index = 0;

    long                    beginTime = 0;
    long                    startTime = 0;
    long                    endTime = 0;
    int                     requests = 0;
    
    private static Logger logger = Logger.getLogger(NewtClient.class);
	
	private String GetDefaultMasterIPFromConfig()
	{
        String newtHome = System.getenv( "NEWT_HOME" );
        if( newtHome == null ) {
           System.out.println( "NewtClient::Configuration not found. Export NEWT_HOME." );
            System.exit( 1 );
        }

	    String configFile = newtHome + "/" + "NewtConfig.xml";
        Node root = null;
        NewtXmlDOM dom = new NewtXmlDOM();
		String masterIP = DEFAULT_MASTER_IP;
		
        try 
		{
            Document doc = dom.doc( configFile );
            root = dom.root( doc );
			Node n = root;
			if(n.getNodeType() == Node.ELEMENT_NODE)
			{
				//try to find master ip
               if( n.getNodeName().equals( "Configuration" ) ) 
			   {
                    n = n.getFirstChild();
                    while( n != null ) 
					{
						if( n.getNodeName().equals( "Nodes" ) ) {
							ArrayList<Node> children = dom.childrenByTag( n, "Master" );
							Node m = children.get( 0 );
							Node v = dom.getAttribute( m, "value" );
							masterIP = v.getNodeValue();
							DEFAULT_MASTER_IP = masterIP;
							break;
						}
                        n = (Node)n.getNextSibling();
                    }
                }
			}
			
			//System.out.println("NewtClient::GetMasterIPFromConfig::IP=" + masterIP);
        } 
		catch( Exception e ) 
		{
           System.out.println( "Failed to process config file." );
           System.exit( 1 );
        }
		
		return masterIP;
	}

    public NewtClient( String masterIP, Mode mode ) {
    	if( masterIP == null ) {
    		this.masterUrl = "http://" + GetDefaultMasterIPFromConfig() + ":8899/hessianrpc";
    	} else {
                this.masterUrl = "http://" + masterIP + ":8899/hessianrpc";
    	}

    	
    	try {
            masterClient = RpcCommunication.getService( masterUrl );
        } catch( Exception e ) {
            e.printStackTrace();
        }

        clientMode = mode;
        if( mode == Mode.CAPTURE ) {
            provenanceContainer = new ProvenanceContainer( maxProv );
            newtProvenanceSender = new NewtProvenanceSender( masterClient );
            provenance = newtProvenanceSender.getProvenanceBuffer();
            fileLocatables = new HashSet<String>();
        }

        beginTime = System.currentTimeMillis();
        endTime = System.currentTimeMillis();
    }
    
    public NewtClient( Mode mode  ) 
	{
    	this( null, mode );
    }

    public NewtClient( String masterIP,  Mode mode, int pid, String aname, String atype, String rid ) {
    	this( masterIP, mode );
    	actorName = aname;
    	actorType = atype;
    	parentID = pid;
    	relativeID = rid == null ? "" : rid;

    	if( clientMode == Mode.CAPTURE ) {
    		try { 
    			actorID = (Integer) masterClient.addActor( parentID, actorName, actorType, relativeID );
    		} catch( Exception e ) {
    			System.err.println( e.getMessage() );
    			e.printStackTrace();
    		}

//    		System.err.println( "NewtClient registered with actorID: " + actorID + " for actor: " 
//    				+ actorName + " with parent: " + parentID + " and relativeID: " + relativeID );
    	}
    }
    
    public NewtClient( Mode mode, int pid, String aname, String atype, String rid, boolean isSubActor ) {
        this( null, mode );
        actorName = aname;
        actorType = atype;
        parentID = pid;
        relativeID = rid == null ? "" : rid;
        this.isSubActor = isSubActor;

        if( clientMode == Mode.CAPTURE ) 
        {
        	// Create a subActor
        	if(isSubActor)
        	{
        		actorID = (Integer) masterClient.addSubActor( parentID, actorName, actorType, relativeID );
        	}
        	else
        	{
	            try 
	            { 
	                actorID = (Integer) masterClient.addActor( parentID, actorName, actorType, relativeID );
	            } 
	            catch( Exception e ) 
	            {
	                System.err.println( e.getMessage() );
	                e.printStackTrace();
	            }
        	}
//            System.err.println( "NewtClient registered with actorID: " + actorID + " for actor: " 
//                + actorName + " with parent: " + parentID + " and relativeID: " + relativeID );
        }
    }
    

    public NewtClient( int tid, int rpid, String aname, String atype, String rid, boolean isSubActor )
    {
        this( Mode.REPLAY, rpid, aname, atype, rid, isSubActor );
        this.traceID = tid;
        this.isSubActor = isSubActor;
        
        String peerUrl = getTraceNode();
//        System.err.println( "NewtClient registered with actorID: " + actorID + " for actor: " 
//            + actorName + " with parent: " + parentID + " and relativeID: " + relativeID );
        
        getReplayFilter( peerUrl );
        System.err.println( "Trace results for trace: " + traceID + " for actor: " + actorID
            + " with table: " + tableName + " retrieved. Total record count: " + replayFilter.size() );
    }

    public Mode getMode()
    {
        return clientMode;
    }

    public void setMode( Mode mode )
    {
        clientMode = mode;
    }

    public void setSchemaID( int sid )
    {
        schemaID = sid;
    }

    public void setTableName( String tname )
    {
        tableName = tname;
    }

    public String getTableName()
    {
        return tableName;
    }

    public int getUniverseID()
    {
        return universeID;
    }

    public void setUniverseName( String uname )
    {
        universeName = uname;
        universeID = getUniverseID( universeName );
    }

    public static int getUniverseID( String uname )
    {
        int uid = -1; 
        try {
            uid = (Integer) RpcCommunication.getService( masterUrl ).getUniverse( uname );
        } catch( Exception e ) {
            System.err.println( e.getMessage() );
            e.printStackTrace();
        }
        return uid;
    }

    /**
     * Registers with the client an actor of type RootActor only.
     * @param aname
     * @param pid
     * @return
     */
    public int register( String aname, int pid )
    {
        actorName = aname;
        actorType = "RootActor";
        parentID = pid;

        try {
            actorID = (Integer) masterClient.register( actorName, parentID );
        } catch( Exception e ) {
            System.err.println( e.getMessage() );
        }
        return actorID;
    }

    public int setProvenanceHierarchy( String hierarchy )
    {
        Integer result = -1;
        try {
            result = (Integer) masterClient.setProvenanceHierarchy( actorID, hierarchy );
        } catch( Exception e ) {
            System.err.println( e.getMessage() );
        }
        return result;
    }

    public int setProvenanceSchemas( String schemas )
    {
        Integer result = -1;
        try {
            result = (Integer) masterClient.setProvenanceSchemas( actorID, schemas );
        } catch( Exception e ) {
            System.err.println( e.getMessage() );
        }
        return result;
    }

    /**
     * Do we need this for subActors?
     * 
     * @param fid
     * @param isSource
     */
    public void addSourceOrDestinationActor( int fid, boolean isSource )
    {
        try { 
            masterClient.addSourceOrDestinationActor( actorID, fid, isSource );
        } catch( Exception e ) {
            System.err.println( e.getMessage() );
        }
    }

    // Zhaomo
    public void addSourceBySubactor(int fid)
    {
    	if (fid < 0)
           return;
    	
        try { 
            masterClient.addSourceBySubactor(actorID, fid);
        } catch( Exception e ) {
            System.err.println( e.getMessage() );
        }
    }



    public int getActorID()
    {
        return actorID;
    }

    public static int getActorID( String aname, String atype )
    {
        int aid = -1;
        try {
            aid = (Integer) RpcCommunication.getService( masterUrl ).getID( aname, atype );
        } catch( Exception e ) {
            System.err.println( e.getMessage() );
        }

        return aid;
    }
    
    public int getParentID()
    {
        return parentID;
    }

    public int getSchemaID()
    {
        return schemaID;
    }

    public void setSchemaID( String uname, String sname )
    {
        schemaID = getSchemaID( uname, sname );
    }
    
    public static int getSchemaID( String uname, String sname )
    {
        int uid = getUniverseID( uname );
        return getSchemaID( uid, sname );
    }

    public static int getSchemaID( int uid, String sname )
    {
        int sid = -1;
        try {
            sid = (Integer) RpcCommunication.getService( masterUrl ).getSchemaID( uid, sname );
        } catch( Exception e ) {
            System.err.println( e.getMessage() );
        }

        return sid;
    }

    public void addFileLocatable( String l, boolean isInput, boolean isGhostable )
    {
        if( !fileLocatables.contains( l ) ) {
            try {
                masterClient.addFileLocatable( actorID, l, isInput, true );
                fileLocatables.add( l );
            } catch( Exception e ) {
                System.err.println( e.getMessage() );
            }
        }
    }

    protected void setInputFileLocatable( I input )
    {
        if( input instanceof FileLocatable ) {
            inputFileLocatable = true;
        }
        isInputFileLocatableSet = true;
    }

    protected void setOutputFileLocatable( O output )
    {
        if( output instanceof FileLocatable ) {
            outputFileLocatable = true;
        }
        isOutputFileLocatableSet = true;
    }

    public void getPeerClient()
    {
        try {
        	String localHostName = InetAddress.getLocalHost().getCanonicalHostName();
        	byte[] localIP = InetAddress.getByName(InetAddress.getLocalHost().getHostAddress()).getAddress(); // <--------------- Vicky: changed to use IP and not host name
//            System.err.println("Newt: NewtClient: local peer IP:"+localHostName);
            //System.out.println("localIP = " + localHostName);
            String peerUrl = (String) masterClient.getProvenanceNode( actorID, tableName, schemaID, localIP, isSubActor );
//            System.err.println("Newt: Assigned peer for actor("+actorID+"), table("+tableName+") is "+peerUrl);
            peerClient = RpcCommunication.getService( peerUrl );
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }
    
       
    public void submitProvenance()
    {
        count += index;
        index = 0;
        requests++;
        
        if( peerClient == null ) {
            getPeerClient();
        }

        synchronized( provenance ) {
//            logger.debug( DateUtils.now("yy/MM/dd HH:mm:ss") + " Submitting request: " + requests );
        	if(isSubActor)
        	{
        		provenance.add( new ProvenanceTask( requests, provenanceContainer, parentID, actorID, schemaID, tableName, peerClient, false ) );	
        	}
        	else
        	{
        		provenance.add( new ProvenanceTask( requests, provenanceContainer, actorID, -1, schemaID, tableName, peerClient, false ) );
        	}
            
            provenance.notifyAll();
        }
        provenanceContainer = new ProvenanceContainer( maxProv );
    }

    public void addProvenance( I input, O output )
    {
        if( input == null || output == null ) {
            return;
        }
        
        if( !isInputFileLocatableSet ) {
            setInputFileLocatable( input );
        }
        if( !isOutputFileLocatableSet ) {
            setOutputFileLocatable( output );
        }
        if( inputFileLocatable ) {
            addFileLocatable( ((FileLocatable) input).getPathname(), true, ((FileLocatable) input).isFileLocation() );
        }
        if( outputFileLocatable ) {
            addFileLocatable( ((FileLocatable) output).getPathname(), false, ((FileLocatable) output).isFileLocation() );
        }

        startTime = endTime;
        endTime = System.currentTimeMillis();
        provenanceContainer.add( new ProvenanceItem( ProvenanceItem.ASSOCIATION, input.getBytes(), output.getBytes(), endTime - startTime ) );
        index++;

        if( index >= maxProv ) {
            submitProvenance();
        }
    }

    public void addInput( I input, String tag )
    {
        if( input == null ) {
            return;
        }
        
        if( !isInputFileLocatableSet ) {
            setInputFileLocatable( input );
        }
        if( inputFileLocatable ) {
            addFileLocatable( ((FileLocatable) input).getPathname(), true, ((FileLocatable) input).isFileLocation() );
        }

        startTime = endTime;
        endTime = System.currentTimeMillis();
        // Zhaomo
        //provenanceContainer.add( new ProvenanceItem( ProvenanceItem.INPUT, input.getBytes(), tag.getBytes(), endTime - startTime ) );
        provenanceContainer.add( new ProvenanceItem( ProvenanceItem.INPUT, input.getBytes(), tag.getBytes(), endTime) );

        index++;

        if( index >= maxProv ) {
            submitProvenance();
        }
    }

    public void addResetAndInput( I input, String tag )
    {
        if( input == null ) {
            addReset( tag );
            return;
        }
        
        if( !isInputFileLocatableSet ) {
            setInputFileLocatable( input );
        }
        if( inputFileLocatable ) {
            addFileLocatable( ((FileLocatable) input).getPathname(), true, ((FileLocatable) input).isFileLocation() );
        }

        startTime = endTime;
        endTime = System.currentTimeMillis();
        provenanceContainer.add( new ProvenanceItem( ProvenanceItem.RESET_INPUT, input.getBytes(), tag.getBytes(), endTime - startTime ) );
        index++;

        if( index >= maxProv ) {
            submitProvenance();
        }
    }

    public void addOutput( O output, String tag )
    {
        if( output == null ) {
            return;
        }
        
        if( !isOutputFileLocatableSet ) {
            setOutputFileLocatable( output );
        }
        if( outputFileLocatable ) {
            addFileLocatable( ((FileLocatable) output).getPathname(), true, ((FileLocatable) output).isFileLocation() );
        }

        startTime = endTime;
        endTime = System.currentTimeMillis();
        // Zhaomo
        //provenanceContainer.add( new ProvenanceItem( ProvenanceItem.OUTPUT, output.getBytes(), tag.getBytes(), endTime - startTime ) );
        provenanceContainer.add( new ProvenanceItem( ProvenanceItem.OUTPUT, output.getBytes(), tag.getBytes(), endTime) );
        index++;

        if( index >= maxProv ) {
            submitProvenance();
        }
    }

    public void addOutputAndReset( O output, String tag )
    {
        if( output == null ) {
            addReset( tag );
            return;
        }
        
        if( !isOutputFileLocatableSet ) {
            setOutputFileLocatable( output );
        }
        if( outputFileLocatable ) {
            addFileLocatable( ((FileLocatable) output).getPathname(), true, ((FileLocatable) output).isFileLocation() );
        }

        startTime = endTime;
        endTime = System.currentTimeMillis();
        provenanceContainer.add( new ProvenanceItem( ProvenanceItem.OUTPUT_RESET, output.getBytes(), tag.getBytes(), endTime - startTime ) );
        index++;

        if( index >= maxProv ) {
            submitProvenance();
        }
    }

    public void addReset( String tag )
    {
        provenanceContainer.add( new ProvenanceItem( ProvenanceItem.RESET, null, tag.getBytes(), 0 ) );
        index++;

        if( index >= maxProv ) {
            submitProvenance();
        }
    }

    public void commit()
    {
        count += index;
        requests++;
        if( peerClient == null ) {
            getPeerClient();
        }
        ProvenanceTask task = null;
        if(isSubActor)
        {
        	task = new ProvenanceTask( requests, provenanceContainer, parentID, actorID, schemaID, tableName, peerClient, true );
        }
        else
        {
        	task = new ProvenanceTask( requests, provenanceContainer, actorID, -1, schemaID, tableName, peerClient, true );
        }

        // Submit remaining provenance associations
        synchronized( provenance ) {
//            logger.debug( DateUtils.now("yy/MM/dd HH:mm:ss") + " Submitting commit request: " + requests );
            provenance.add( task );
            provenance.notifyAll();
        }

        // Wait for all provenance requests and commit request to be completed
        // Neccessary for ensuring that an actor doesn't commit without its provenance
        // committing
       
        synchronized( task ) {
            while( task.committed() != true ) {
                try {
                    task.wait();
                } catch( Exception e ) {
                }
            }
        }
        
        try {
//            logger.debug( DateUtils.now("yy/MM/dd HH:mm:ss") + " Committing actor" );
            masterClient.commit( actorID, isSubActor );
        } catch( Exception e ) {
            System.err.println( e.getMessage() );
        }

        float avgTime = ((float)(System.currentTimeMillis() - beginTime)) / count;
        float processRate = ((float)count) / (System.currentTimeMillis() - beginTime);
        //System.err.println( "Actor: " + actorID + ": Provenance count = " + count + ", time/assoc: " + avgTime + " ms, assoc/time: " + processRate + " ms" );
    }

    public void rootCommit()
    {
        try {
            masterClient.commit( actorID, false);
        } catch( Exception e ) {
            System.err.println( e.getMessage() );
        }

       //System.out.println("RootActor: actorID=" + actorID + " committed");
    }



    public static int trace( Vector data, String direction, int cid, int aid )
    {
        return trace( data, direction, cid, aid, null);
    }

    public static int trace( Vector data, String direction, int cid, String atype )
    {
        return trace( data, direction, cid, -1, atype );
    }

    public static int trace( Vector data, String direction, int cid, int aid, String atype )
    {
        int tid= -1;
        try {
//        	masterUrl = "http://" + "192.168.1.76" + ":8899/hessianrpc";
        	masterUrl = "http://" + Utilities.GetDefaultMasterIPFromConfig() + ":8899/hessianrpc";;

            tid = (Integer) RpcCommunication.getService( masterUrl ).trace( data, direction, cid, aid, atype );
        } catch( Exception e ) {
            System.err.println( e.getMessage() );
        }

        return tid;
    }

    public String getTraceNode()
    {
        String peerUrl = null;
        try { 
            Object[] result = (Object[]) masterClient.getTraceNode( traceID, parentID, actorType, relativeID );
            actorID = (Integer) result[ 0 ];
            tableName = (String) result[ 1 ];
            peerUrl = (String) result[ 2 ];
        } catch( Exception e ) {
            System.err.println( e.getMessage() );
            e.printStackTrace();
        }

        return peerUrl;
    }

    public void getReplayFilter( String peerUrl )
    {
        try {
            replayFilter = (HashSet) RpcCommunication.getService( peerUrl ).getReplayFilter( traceID, tableName );
        } catch( Exception e ) {
            System.err.println( e.getMessage() );
            e.printStackTrace();
        }
    }

    public static void printTraceResults( int traceID, String actorName )
    {
        HashSet replayFilter = null;
        try {
            Object[] result = (Object[]) RpcCommunication.getService( masterUrl ).getTraceNodeByActorID( traceID, actorName );
            String peerUrl = (String) result[ 0 ];
            String tableName = (String) result[ 1 ];
            replayFilter = (HashSet) RpcCommunication.getService( peerUrl ).getReplayFilter( traceID, tableName );
        } catch( Exception e ) {
            System.err.println( e.getMessage() );
            e.printStackTrace();
        }

        for( Object o: replayFilter ) {
            if( o instanceof ByteArray ) {
            	String output = new String( ((ByteArray)o).getBytes() );
            	System.out.println("NewtClient::printTraceResults1: " + output);
                //logger.debug( output );
            } else {
            	System.out.println("NewtClient::printTraceResults2: " + o.toString());
                logger.debug( o.toString() );
            }
        }

        System.out.println("NewtClient::printTraceResults3:Total: " + replayFilter.size() + " records found.");
        logger.debug( "Total " + replayFilter.size() + " records found.\n" );
    }

    public boolean allowInput( I input )
    {
        if( replayFilter != null ) {
            return replayFilter.contains( input.toProvenance() );
        } else {
            return true;
        }
    }
}