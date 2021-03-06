package com.appdynamics.extensions.rabbitmq;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.rabbitmq.conf.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.conf.Instances;
import com.appdynamics.extensions.rabbitmq.conf.QueueGroup;
import com.appdynamics.extensions.util.MetricWriteHelper;
import com.appdynamics.extensions.util.MetricWriteHelperFactory;
import com.google.common.base.Strings;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;

/**
 * Created with IntelliJ IDEA.
 * User: abey.tom
 * Date: 11/20/13
 * Time: 5:02 PM
 * To change this template use File | Settings | File Templates.
 */
public class RabbitMQMonitor extends AManagedMonitor {
	public static final Logger logger = Logger.getLogger("com.singularity.extensions.rabbitmq.RabbitMQMonitor");
	public static final String DEFAULT_METRIC_PREFIX = "Custom Metrics|RabbitMQ|";

	private String metricPrefix = DEFAULT_METRIC_PREFIX;

	private MonitorConfiguration configuration;

	//Holds the Key-Description Mapping
	private Map<String, String> dictionary;



	private boolean initialized;
	protected Instances instances = new Instances();

	public RabbitMQMonitor() {
		String msg = "Using Monitor Version [" + getImplementationVersion() + "]";
		logger.info(msg);
		System.out.println(msg);
		dictionary = new HashMap<String, String>();
		dictionary.put("ack", "Acknowledged");
		dictionary.put("deliver", "Delivered");
		dictionary.put("deliver_get", "Delivered (Total)");
		dictionary.put("deliver_no_ack", "Delivered No-Ack");
		dictionary.put("get", "Got");
		dictionary.put("get_no_ack", "Got No-Ack");
		dictionary.put("publish", "Published");
		dictionary.put("redeliver", "Redelivered");
		dictionary.put("messages_ready", "Available");
		dictionary.put("messages_unacknowledged", "Pending Acknowledgements");
		dictionary.put("consumers", "Count");
		dictionary.put("active_consumers", "Active");
		dictionary.put("idle_consumers", "Idle");
		dictionary.put("slave_nodes", "Slaves Count");
		dictionary.put("synchronised_slave_nodes", "Synchronized Slaves Count");
		dictionary.put("down_slave_nodes", "Down Slaves Count");
		dictionary.put("messages", "Messages");
	}

	private void configure(Map<String, String> argsMap) {
		logger.info("Initializing the RabbitMQ Configuration");
		MetricWriteHelper metricWriteHelper = MetricWriteHelperFactory.create(this);
		if(!Strings.isNullOrEmpty(argsMap.get("metricPrefix"))){
			metricPrefix = argsMap.get("metricPrefix");
		}
		MonitorConfiguration conf = new MonitorConfiguration(metricPrefix, new TaskRunnable(), metricWriteHelper);
		String configFileName = argsMap.get("config-file");
		if(Strings.isNullOrEmpty(configFileName)){
			configFileName = "monitors/RabbitMQMonitor/config.yml";
		}
		conf.setConfigYml(configFileName);
		conf.checkIfInitialized(MonitorConfiguration.ConfItem.CONFIG_YML, MonitorConfiguration.ConfItem.EXECUTOR_SERVICE,
				MonitorConfiguration.ConfItem.METRIC_PREFIX, MonitorConfiguration.ConfItem.METRIC_WRITE_HELPER);
		this.configuration = conf;
		initialized = true;
	}

	private class TaskRunnable implements Runnable {

		public void run() {
			Map<String, ?> config = configuration.getConfigYml();
			if(config!=null){
				for(InstanceInfo info : instances.getInstances()){
					configuration.getExecutorService().execute(new RabbitMQMonitoringTask(configuration, info,dictionary,instances.getQueueGroups(),metricPrefix));
				}
			}
			else{
				logger.error("Configuration not found");
			}
		}
	}
	public TaskOutput execute(Map<String, String> argsMap, TaskExecutionContext executionContext) throws TaskExecutionException {
		if (!initialized) {
			configure(argsMap);
		}
		initialiseInstances(this.configuration.getConfigYml());
		logger.info("Starting the RabbitMQ Metric Monitoring task");
		argsMap = checkArgs(argsMap);
		if (logger.isDebugEnabled()) {
			logger.debug("The arguments after appending the default values are " + argsMap);
		}
		configuration.executeTask();
		return new TaskOutput("RabbitMQ Metric Upload Complete ");
	}

	/**
	 * Defaults the value if not present.
	 *
	 * @param argsMapsActual
	 * @return
	 */
	protected Map<String, String> checkArgs(Map<String, String> argsMapsActual) {
		Map<String, String> newArgsMap;
		if (argsMapsActual != null) {
			newArgsMap = new HashMap<String, String>(argsMapsActual);
		} else {
			newArgsMap = new HashMap<String, String>();
		}
		String prefix = newArgsMap.get("metricPrefix");
		if (prefix == null) {
			newArgsMap.put("metricPrefix", RabbitMQMonitor.DEFAULT_METRIC_PREFIX);
		} else {
			String trim = prefix.trim();
			Pattern compile = Pattern.compile("(.+?)(\\|+)");
			Matcher matcher = compile.matcher(trim);
			if (matcher.matches()) {
				trim = matcher.group(1);
			}
			newArgsMap.put("metricPrefix", trim + "|");
		}
		return newArgsMap;
	}

	private void initialiseInstances(Map<String, ?> configYml) {
		List<Map<String,?>> instances = (List<Map<String, ?>>) configYml.get("servers");
		if(instances!=null && instances.size()>0){
			int index = 0;
			InstanceInfo[] instancesToSet = new InstanceInfo[instances.size()];
			for(Map<String,?> instance : instances){
				InstanceInfo info = new InstanceInfo();
				if(Strings.isNullOrEmpty((String) instance.get("displayName"))){
					logger.error("Display name not mentioned for server ");
					throw new RuntimeException("Display name not mentioned for server");
				}
				else{
					info.setDisplayName((String) instance.get("displayName"));
				}
				if(!Strings.isNullOrEmpty((String) instance.get("host"))){
					info.setHost((String) instance.get("host"));
				}
				else{
					info.setHost("localhost");
				}
				if(!Strings.isNullOrEmpty((String) instance.get("username"))){
					info.setUsername((String) instance.get("username"));
				}
				else{
					info.setUsername("guest");
				}

				if(!Strings.isNullOrEmpty((String) instance.get("password"))){
					info.setPassword((String) instance.get("password"));
				}
				else{
					info.setPassword("guest");
				}
				if(instance.get("port")!=null){
					info.setPort((Integer) instance.get("port"));
				}
				else{
					info.setPort(15672);
				}
				if(instance.get("useSSL")!=null){
					info.setUseSSL((Boolean) instance.get("useSSL"));
				}
				else{
					info.setUseSSL(false);
				}
				if(instance.get("connectTimeout")!=null){
					info.setConnectTimeout((Integer) instance.get("connectTimeout"));
				}
				else{
					info.setConnectTimeout(10000);
				}
				if(instance.get("socketTimeout")!=null){
					info.setSocketTimeout((Integer) instance.get("connectTimeout"));
				}
				else{
					info.setSocketTimeout(10000);
				}
				instancesToSet[index++] = info;
			}
			this.instances.setInstances(instancesToSet);
		}
		else{
			logger.error("no instances configured");
		}
		List<Map<String,?>> queueGroups = (List<Map<String, ?>>) configYml.get("queueGroups");
		if(queueGroups!=null && queueGroups.size()>0){
			int index = 0;
			QueueGroup[] groups =new QueueGroup[queueGroups.size()];
			for(Map<String,?> group : queueGroups){
				QueueGroup g = new QueueGroup();
				g.setGroupName((String) group.get("groupName"));
				g.setQueueNameRegex((String) group.get("queueNameRegex"));
				g.setShowIndividualStats((Boolean) group.get("showIndividualStats"));
				groups[index++] = g;
			}
			this.instances.setQueueGroups(groups);
		}
		else{
			logger.debug("no queue groups defined");
		}

	}

	public static String getImplementationVersion() {
		return RabbitMQMonitor.class.getPackage().getImplementationTitle();
	}
}
