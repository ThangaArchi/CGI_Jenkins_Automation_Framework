package com.castsoftware.dmtexplore.util;

import java.io.OutputStream;
import java.io.PrintWriter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The Class OptionsValidation validates the options provided in the command
 * line
 * 
 * @author FME
 * @version 1.1
 */

public class OptionsValidation implements Constants
{

	/** The log. */
	public static Log log = LogFactory.getLog(OptionsValidation.class);

	private CommandLineParser parser;
	private CommandLine line;
	private String[] args;
	private Options options;

	/**
	 * Instantiates a new options validation.
	 * 
	 * @param args
	 *            the args
	 * @throws ParseException
	 */
	public OptionsValidation(String[] args) 
	{
		try {
			setArgs(args);
			options = createOptions();
			parser = new GnuParser();
			line = parser.parse(options, args);
			validation();
		} catch (ParseException e) {
			log.error(e.getMessage());
			printUsage(System.out);
		}
	}

	private Options createOptions()
	{
		options = new Options();

		options.addOption("h", false, "Display usage");
		
		options.addOption(DMT_LOCATION, true, "Base location of the Delivery Manager");
		options.getOption(DMT_LOCATION).setRequired(true);
		options.addOption(OUTPUT_FILE, true, "Results destination file");
		options.getOption(OUTPUT_FILE).setRequired(true);

		options.addOption(APPLICATION_NAME, true, "Select a specific application to examine");
		options.addOption(CURRENT_TO_BASE, false,
				"Compare all packages in the first version to the latest delivery versions");
		options.addOption(CURRENT_TO_PREV, false,
				"Compare all packages in the latest delivery to the previous version");
//		options.addOption(VERSION_A, true, "A delivery version to compare");
//		options.addOption(VERSION_B, true, "Another delivery version to compare");

		return options;
	}

	public void printUsage(final OutputStream out)
	{
		final PrintWriter writer = new PrintWriter(out);
		final HelpFormatter usageFormatter = new HelpFormatter();
		String syntax = "xmlExplore -dmtRoot <arg> -outputFile <arg>  [-curentToBase | -curentToPrev] [-application <arg>]";

		usageFormatter.printHelp(writer, 80, syntax, "", options, 5, 5, "");
		writer.close();
	}

	/**
	 * Gets the options validation.
	 * 
	 * @param options
	 *            the options
	 * @return the options validation
	 * @throws ParseException
	 *             the parse exception
	 */
	public boolean validation() throws ParseException
	{

		try {
			if (line.hasOption("h")) { // No need to ask for the parameter
				// "help". Both are synonyms
				throw new ParseException("");
			}

			if (line.hasOption(VERSION)) { // No need to ask for the parameter
				throw new ParseException("");
			}

			if (getDMTRoot().isEmpty()) {
				throw new org.apache.commons.cli.ParseException(
						"Required parameter -dmtRoot is missing");
			}

			if (getOutputFile().isEmpty()) {
				throw new org.apache.commons.cli.ParseException(
						"Required parameter -outputFile is missing");
			}

			if (getApplicationName().isEmpty()) {
				if ((!getVersionA().isEmpty() || !getVersionB().isEmpty())) {
					throw new org.apache.commons.cli.ParseException(
							"Required parameter -application is empty.\n\tSpecific versions can only be compared for an individual application");
				}
			}

			String appName = line.getOptionValue(APPLICATION_NAME);
			if (appName == null || appName.isEmpty()) {
				appName = "all";
			}
//			Configuration updateConfig = new Configuration();
//			updateConfig.setUpdateLog4jConfiguration(line.getOptionValue(LOG_PATH),
//					line.hasOption(DEBUG), line.getOptionValue(LOG_OUTPUT), appName);

		} catch (ParseException exp) {
			// Something went wrong
			throw exp;
		}
		return true;
	}

	public String getDMTRoot()
	{
		return line.getOptionValue(DMT_LOCATION, "");
	}

	public String getOutputFile()
	{
		return line.getOptionValue(OUTPUT_FILE, "");
	}

	
	
	public String getApplicationName()
	{
		return line.getOptionValue(APPLICATION_NAME, "");
	}

	public boolean isCurrentToBase()
	{
		return line.hasOption(CURRENT_TO_BASE);
	}

	public boolean isCurrentToPrevious()
	{
		return line.hasOption(CURRENT_TO_PREV);
	}

	public String getVersionA()
	{
		return line.getOptionValue(VERSION_A, "");
	}

	public String getVersionB()
	{
		return line.getOptionValue(VERSION_B, "");
	}

	/**
	 * Sets the command line.
	 * 
	 * @param line
	 *            the new command line
	 */
	public void setCommandLine(CommandLine line)
	{
		this.line = line;
	}

	/**
	 * Gets the args.
	 * 
	 * @return the args
	 */
	public String[] getArgs()
	{
		return args;
	}

	/**
	 * Sets the args.
	 * 
	 * @param args
	 *            the args to set
	 */
	public void setArgs(String[] args)
	{
		this.args = args;
	}

}
