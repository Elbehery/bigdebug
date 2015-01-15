package newt.client;

import java.lang.*;
import java.util.*;

import newt.actor.*;
import newt.client.NewtClient.Mode;

public class NewtStageClient<I extends ProvenanceDataType, O extends ProvenanceDataType> {
    NewtClient<I, O>  newtClient = null;
    Object trgr = null;

    public NewtStageClient( NewtClient<I, O> newtClient ) 
    { 
        this.newtClient = newtClient;
    }

    public NewtClient getNewtClient()
    {
        return newtClient;
    }
   
    public boolean allowInput( I input )
    {
        if( newtClient.getMode() == Mode.REPLAY ) {
            return newtClient.allowInput( input );
        }

        return true;
    }
   
    public void addInput(I input) {
        newtClient.addInput(input, "");
    }

    public void addInput( Object trigger, I input ) 
    {
        if( !trigger.equals( trgr ) ) {
            trgr = trigger;
            newtClient.addResetAndInput( input, "" );
        } else {
            newtClient.addInput( input, "" );
        }
    }

    public void addTaggedInput( String tag, I input )
    {
        newtClient.addInput( input, tag );
    }

    public void addOutput( O output )
    {
        newtClient.addOutput( output, "" );
    }

    public void addOutputAndFlush( O output )
    {
        newtClient.addOutputAndReset( output, "" );
    }

    public void addTaggedOutput( String tag, O output )
    {
        newtClient.addOutput( output, tag );
    }

    public void addTaggedOutputAndFlush( String tag, O output )
    {
        newtClient.addOutputAndReset( output, tag );
    }
}
