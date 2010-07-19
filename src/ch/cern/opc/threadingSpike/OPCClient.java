package ch.cern.opc.threadingSpike;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import cern.ess.opclib.OpcApi;

public class OPCClient {

	private final SynchronousQueue<OPCCommand> requestQueue;
	private final ExecutorService threadRunner;
	private Future<Integer> threadResult;
	private final OpcCommandFactory commandFactory;
	
	private final static int MAX_THREAD_SHUTDOWN_WAIT_MS = 250;
	
	/**
	 * Each thread calling the OPC client has its own response queue (embedded
	 * in a thread local)
	 */
	private static ThreadLocal<SynchronousQueue<OPCCommandResult>> threadResponseQueue = new ThreadLocal<SynchronousQueue<OPCCommandResult>>()
	{
		@Override
		protected SynchronousQueue<OPCCommandResult> initialValue() 
		{
			System.out.println("Creating a response queue for thread ["+Thread.currentThread().getId()+"]");
			return new SynchronousQueue<OPCCommandResult>();
		};		
	};
	
	public OPCClient(OpcApi opcInterface) 
	{
		this.requestQueue = new SynchronousQueue<OPCCommand>();
		this.commandFactory = new OpcCommandFactory(opcInterface, requestQueue);
		
		this.threadRunner = Executors.newFixedThreadPool(1);
		
	}
	
	public void start()
	{
		threadResult = threadRunner.submit(new CommandExecutor());
	}
	
	public int stop()
	{	
		System.out.println("Stopping OPC Client thread...");
		threadRunner.shutdownNow();
		requestQueue.clear();
	
		int result = -1;
		try 
		{
			Integer objectResult = threadResult.get(MAX_THREAD_SHUTDOWN_WAIT_MS, TimeUnit.MILLISECONDS);
			if(objectResult != null)
			{
				result = objectResult.intValue();
			}
		} 
		catch (InterruptedException e){} 
		catch (ExecutionException e){} 
		catch (TimeoutException e){}
		
		System.out.println("command queue consumer task stopped - processed ["+result+"] commands");
		
		return result;
	}
	
	/**
	 * Called by external threads to schedule a command for the OPC client.
	 * The OPC client runs the command when it is ready.
	 * 
	 * @param command
	 * @throws InterruptedException
	 */
	public boolean submitCommand(OPCCommand command) 
	{
		try 
		{
			requestQueue.put(command);
			return true;
		} 
		catch (InterruptedException e) 
		{
			return false;
		}
	}
	
	private class CommandExecutor implements Callable<Integer>
	{

		/**
		 * task loops taking commands from the command queue and processing them.
		 * Is killed by a thread interrupt.
		 */
		@Override
		public Integer call() throws Exception {
			System.out.println("OPC Client task for executing commands started");
			int numberOfCommandsProcessed = 0;

			try 
			{
				while(true)
				{
					System.out.println("waiting for command...");
					OPCCommand command = requestQueue.take();
					command.execute();
					numberOfCommandsProcessed++;
				}
			} 
			catch (InterruptedException e) 
			{
				System.out.println("Task interrupted - exiting");
			}

			System.out.println("OPC Client thread stopped");
			return Integer.valueOf(numberOfCommandsProcessed);
		}		
	}
	
	public boolean readBoolean(final String opcItemAddress) 
	{
		OPCCommand command = 
			commandFactory.createReadBooleanCommand(
					opcItemAddress, 
					threadResponseQueue.get()); 

		Object result = command.scheduleAndWaitForResponse();		
		return ((Boolean)result).booleanValue();			
	}

	public String[] getItemNames() {
		OPCCommand command = commandFactory.createGetItemNamesCommand(threadResponseQueue.get());	
		Object result = command.scheduleAndWaitForResponse();		
		return (String[]) result;
	}

	public String[] getLocalServerList() 
	{
		OPCCommand command = commandFactory.createGetLocalServerList(threadResponseQueue.get());
		Object result = command.scheduleAndWaitForResponse();
		return (String[]) result;
	}

	public float readFloat(String opcItemAddress)
	{
		OPCCommand command = commandFactory.createReadFloatCommand(opcItemAddress, threadResponseQueue.get());
		Object result = command.scheduleAndWaitForResponse();
		return ((Float)result).floatValue();
	}

	public Object readInt(String opcItemAddress) 
	{
		OPCCommand command = commandFactory.createReadIntCommand(opcItemAddress, threadResponseQueue.get());
		Object result = command.scheduleAndWaitForResponse();
		return ((Integer)result).intValue();
	}

	public Object readString(String opcItemAddress) 
	{
		OPCCommand command = commandFactory.createReadStringCommand(opcItemAddress, threadResponseQueue.get());
		Object result = command.scheduleAndWaitForResponse();
		return result;
	}

	public void writeBoolean(String opcItemAddress, boolean value) 
	{
		OPCCommand command = commandFactory.createWriteBooleanCommand(opcItemAddress, threadResponseQueue.get(), value);
		command.scheduleAndWaitForResponse();
	}

	public void writeFloat(String opcItemAddress, float value, String floatType) 
	{
		OPCCommand command = commandFactory.createWriteFloatCommand(opcItemAddress, threadResponseQueue.get(), value, floatType);
		command.scheduleAndWaitForResponse();
	}

	public void writeInt(String opcItemAddress, int value, String intType) 
	{
		OPCCommand command = commandFactory.createWriteIntCommand(opcItemAddress, threadResponseQueue.get(), value, intType);
		command.scheduleAndWaitForResponse();
	}

	public void writeString(String opcItemAddress, String value) 
	{
		OPCCommand command = commandFactory.createWriteStringCommand(opcItemAddress, threadResponseQueue.get(), value);
		command.scheduleAndWaitForResponse();
	}	
}