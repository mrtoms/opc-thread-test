package ch.cern.opc.threadingSpike;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import cern.ess.opclib.OPCException;
import cern.ess.opclib.OpcApi;

public class OPCClientTest
{
	private SynchronousQueue<OPCCommandResult> responseQueue;

	private OPCClient testee;
	private MockOpcApiImpl mockOpcApi;
	
	public final static String OPC_BOOLEAN_ITEM_ADDRESS_TRUE = "this.that.boolean.value.true";
	public final static String OPC_BOOLEAN_ITEM_ADDRESS_FALSE = "this.that.boolean.value.false";
	
	@Before
	public void setup()
	{
		responseQueue = new SynchronousQueue<OPCCommandResult>();
		
		mockOpcApi = new MockOpcApiImpl();
			
		testee = new OPCClient(mockOpcApi);
		testee.start();
	}
	
	@After
	public void teardown()
	{
		testee.stop();
	}
	
	@Test
	public void testReadBooleanRequestsCorrectItem() throws InterruptedException
	{
		assertEquals(0, responseQueue.size());

		String itemAddress = "My.Boolean.Item.Address"; 
		mockOpcApi.getOpcItemValues().put(itemAddress, Boolean.TRUE);
		
		testee.readBoolean(itemAddress);
		
		assertEquals(1, mockOpcApi.getRequestedItems().size());
		assertEquals(itemAddress, mockOpcApi.getRequestedItems().get(0));
	}

	@Test
	public void testMultipleReadBooleanCommandsFromSingleThread() throws InterruptedException
	{	
		String opcItemAddress = "My.Repeat.Test";
		mockOpcApi.getOpcItemValues().put(opcItemAddress, Boolean.TRUE);

		for(int i=0; i<100; i++)
		{
			testee.readBoolean(opcItemAddress);
		}
		
		List<String> requestedItems = mockOpcApi.getRequestedItems();
		assertEquals(100, requestedItems.size());
		
		for(Iterator<String> iterator = requestedItems.iterator(); iterator.hasNext();)
		{
			assertEquals(opcItemAddress, iterator.next());
		}
		
	}

	@Test
	public void testMultipleReadBooleanCommandsFromMultipleThreads() throws InterruptedException, ExecutionException
	{
		Collection<CommandRequester> tasks = new ArrayList<CommandRequester>();
		for(int i=0; i<500; i++)
		{
			String opcItemAddress = "Boolean.OPC.Item.Number."+i;
			mockOpcApi.getOpcItemValues().put(opcItemAddress, Boolean.TRUE);
			tasks.add(new CommandRequester(testee, opcItemAddress));
		}
		
		// split tasks amongst 5 threads
		ExecutorService threadRunner = Executors.newFixedThreadPool(5);
		List<Future<Boolean>> results = threadRunner.invokeAll(tasks);
		
		for(Iterator<Future<Boolean>> iter = results.iterator(); iter.hasNext(); )
		{
			Future<Boolean> result = iter.next();
			try 
			{
				assertTrue("task result was not true", result.get(5, TimeUnit.SECONDS));
			} 
			catch (TimeoutException e) 
			{
				fail("Thread failed to complete within time limit");
			}			
		}
	}
	
	@Test
	public void testGetItemNames()
	{
		String itemNames[] = {"item.1", "item.2", "item.3", "item.4", "item.5"};
		for(int i=0; i<itemNames.length; i++)
		{
			mockOpcApi.getOpcItemValues().put(itemNames[i], null);
		}
		
		String result[] = testee.getItemNames();		
		assertEquals(itemNames.length, result.length);

		List<String> searchableResult = asList(result);
		assertTrue(searchableResult.containsAll(asList(itemNames)));
	}
	
	@Test
	public void testGetLocalServerList()
	{
		String serverList[] = {"server.1", "server.2", "server.3"};
		mockOpcApi.setLocalServerList(serverList);
		
		String result[] = testee.getLocalServerList();
		assertEquals(serverList.length, result.length);
		
		List<String> searchableResult = asList(result);
		assertTrue(searchableResult.containsAll(asList(serverList)));		
	}
	
	@Test
	public void testReadFloat()
	{
		Map<String, Object> opcItemValues = mockOpcApi.getOpcItemValues();
		opcItemValues.put("opc.item.float1", (float)1.0);
		opcItemValues.put("opc.item.float2", (float)2.0);
		opcItemValues.put("opc.item.float3", (float)3.0);
				
		assertEquals((float)1.0, testee.readFloat("opc.item.float1"), 0.00001);
		assertEquals((float)2.0, testee.readFloat("opc.item.float2"), 0.00001);
		assertEquals((float)3.0, testee.readFloat("opc.item.float3"), 0.00001);
	}
	
	@Test
	public void testReadInt()
	{
		Map<String, Object> opcItemValues = mockOpcApi.getOpcItemValues();
		opcItemValues.put("opc.item.int.1", 1);
		opcItemValues.put("opc.item.int.2", 2);
		opcItemValues.put("opc.item.int.3", 3);
		
		assertEquals(1, testee.readInt("opc.item.int.1"));
		assertEquals(2, testee.readInt("opc.item.int.2"));
		assertEquals(3, testee.readInt("opc.item.int.3"));
	}
	
	@Test
	public void testReadString()
	{
		Map<String, Object> opcItemValues = mockOpcApi.getOpcItemValues();
		opcItemValues.put("opc.item.string.1", "one");
		opcItemValues.put("opc.item.string.2", "two");
		opcItemValues.put("opc.item.string.3", "three");
		
		assertEquals("one", testee.readString("opc.item.string.1"));
		assertEquals("two", testee.readString("opc.item.string.2"));
		assertEquals("three", testee.readString("opc.item.string.3"));		
	}
	
	@Test
	public void testWriteBoolean()
	{
		Map<String, Object> opcItemValues = mockOpcApi.getOpcItemValues();
		opcItemValues.put("opc.item.boolean.1", false);
		opcItemValues.put("opc.item.boolean.2", false);
		opcItemValues.put("opc.item.boolean.3", false);
		
		testee.writeBoolean("opc.item.boolean.1", true);
		testee.writeBoolean("opc.item.boolean.2", true);
		testee.writeBoolean("opc.item.boolean.3", true);
		
		assertTrue((Boolean)opcItemValues.get("opc.item.boolean.1"));
		assertTrue((Boolean)opcItemValues.get("opc.item.boolean.2"));
		assertTrue((Boolean)opcItemValues.get("opc.item.boolean.3"));
	}
	
	@Test
	public void testWriteFloat()
	{
		Map<String, Object> opcItemValues = mockOpcApi.getOpcItemValues();
		opcItemValues.put("opc.item.float.1", (float)1.0);
		opcItemValues.put("opc.item.float.2", (float)2.0);
		opcItemValues.put("opc.item.float.3", (float)3.0);
		
		testee.writeFloat("opc.item.float.1", (float)101.0, "some float type");
		testee.writeFloat("opc.item.float.2", (float)102.0, "some float type");
		testee.writeFloat("opc.item.float.3", (float)103.0, "some float type");
		
		float f1 = ((Float) opcItemValues.get("opc.item.float.1")).floatValue();
		assertEquals((float)101.0, f1, 0.001);

		float f2 = ((Float) opcItemValues.get("opc.item.float.2")).floatValue();
		assertEquals((float)102.0, f2, 0.001);

		float f3 = ((Float) opcItemValues.get("opc.item.float.3")).floatValue();
		assertEquals((float)103.0, f3, 0.001);
	}
	
	@Test
	public void testWriteInt()
	{
		Map<String, Object> opcItemValues = mockOpcApi.getOpcItemValues();
		opcItemValues.put("opc.item.int.1", 1);
		opcItemValues.put("opc.item.int.2", 2);
		opcItemValues.put("opc.item.int.3", 3);
		
		testee.writeInt("opc.item.int.1", 101, "some int type");
		testee.writeInt("opc.item.int.2", 102, "some int type");
		testee.writeInt("opc.item.int.3", 103, "some int type");

		assertEquals(101, ((Integer) opcItemValues.get("opc.item.int.1")).intValue());
		assertEquals(102, ((Integer) opcItemValues.get("opc.item.int.2")).intValue());
		assertEquals(103, ((Integer) opcItemValues.get("opc.item.int.3")).intValue());
	}
	
	@Test
	public void testWriteString()
	{
		Map<String, Object> opcItemValues = mockOpcApi.getOpcItemValues();
		opcItemValues.put("opc.item.string.1", "one");
		opcItemValues.put("opc.item.string.2", "two");
		opcItemValues.put("opc.item.string.3", "three");
		
		testee.writeString("opc.item.string.1", "a");
		testee.writeString("opc.item.string.2", "b");
		testee.writeString("opc.item.string.3", "c");

		assertEquals("a", opcItemValues.get("opc.item.string.1"));
		assertEquals("b", opcItemValues.get("opc.item.string.2"));
		assertEquals("c", opcItemValues.get("opc.item.string.3"));
		}
	
	private static class MockOpcApiImpl implements OpcApi
	{
		private String[] localServerList;
		private List<String> requestedItems;
		private final Map<String, Object> opcItemValues;
		
		public MockOpcApiImpl()
		{
			requestedItems = new ArrayList<String>();
			this.opcItemValues = new HashMap<String, Object>(); 
		}
		
		public void setLocalServerList(String[] serverList) 
		{
			this.localServerList = serverList;
		}

		public List<String> getRequestedItems()
		{
			return requestedItems;
		}
		
		public Map<String, Object> getOpcItemValues()
		{
			return opcItemValues;
		}

		@Override
		public String[] getItemNames() throws OPCException 
		{
			Set<String> itemNames = opcItemValues.keySet();
			return itemNames.toArray(new String[0]);
		}

		@Override
		public String[] getLocalServerList() throws OPCException 
		{
			return localServerList;
		}

		@Override
		public boolean readBoolean(String item) throws OPCException 
		{
			return ((Boolean)getRequestedItem(item)).booleanValue();				
		}

		@Override
		public float readFloat(String item) throws OPCException 
		{
			return ((Float)getRequestedItem(item)).floatValue();
		}

		@Override
		public int readInt(String item) throws OPCException 
		{
			return ((Integer)getRequestedItem(item)).intValue();
		}

		@Override
		public String readString(String item) throws OPCException 
		{
			return (String)getRequestedItem(item);
		}

		@Override
		public void writeBoolean(String item, boolean val) throws OPCException 
		{
			setRequestedItem(item, Boolean.valueOf(val));
		}

		@Override
		public void writeFloat(String item, String type, float val) throws OPCException 
		{
			setRequestedItem(item, Float.valueOf(val));
		}

		@Override
		public void writeInt(String item, String type, int val) throws OPCException 
		{
			setRequestedItem(item, Integer.valueOf(val));
		}

		@Override
		public void writeString(String item, String val) throws OPCException 
		{
			setRequestedItem(item, val);
		}	
		
		private Object getRequestedItem(final String opcItemAddress) throws OPCException
		{
			requestedItems.add(opcItemAddress);
			Object value = opcItemValues.get(opcItemAddress);
			
			if(value == null)
			{
				throw new OPCException("failed to find opc item ["+opcItemAddress+"]");
			}
			
			return value;
		}
		
		private void setRequestedItem(final String opcItemAddress, Object value) throws OPCException
		{
			if(!opcItemValues.containsKey(opcItemAddress))
			{
				throw new OPCException("failed to find opc item ["+opcItemAddress+"]");				
			}
			
			opcItemValues.put(opcItemAddress, value);
		}
	}

}