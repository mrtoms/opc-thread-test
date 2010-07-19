package ch.cern.opc.threadingSpike;

import java.util.concurrent.SynchronousQueue;

import cern.ess.opclib.OPCException;
import cern.ess.opclib.OpcApi;

public class GetItemNamesCommand extends OPCCommandBase implements OPCCommand {


	public GetItemNamesCommand(OpcApi opcInterface,
			SynchronousQueue<OPCCommand> reqQueue,
			SynchronousQueue<OPCCommandResult> rspQueue) {
		super(opcInterface, "!not item specific!", reqQueue, rspQueue);
	}

	@Override
	public String getCommandName() 
	{
		return "GetItemNames";
	}

	@Override
	public void execute() throws OPCException, InterruptedException
	{
		String[] itemNames = getOpcApi().getItemNames();
		responseQueue.put(new OPCCommandResult(true, itemNames));
	}
}
