/*-
 * #%L
 * Mars importer to convert Single-molecule Dataset (SMD) format to Molecule Archive format
 * %%
 * Copyright (C) 2022 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package de.mpg.biochem.mars.molecule.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DoubleColumn;

import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.molecule.SingleMolecule;
import de.mpg.biochem.mars.molecule.SingleMoleculeArchive;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;

@Plugin(type = Command.class, label = "Single-molecule dataset (SMD)", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Mars", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 'm'),
		@Menu(label = "Import", weight = 110,
			mnemonic = 'i'),
		@Menu(label = "Single-molecule dataset (SMD)", weight = 10, mnemonic = 's')})
public class ImportSMDCommand extends DynamicCommand implements Command {

	@Parameter
	private LogService logService;
	
	@Parameter(label = "Single-molecule dataset (SMD) json file")
	private File file;
	
	/**
	 * OUTPUTS
	 */
	@Parameter(label = "Molecule Archive", type = ItemIO.OUTPUT)
	private SingleMoleculeArchive archive;

	@Override
	public void run() {
		//Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();

		//Build log message
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("Single-molecule dataset (SMD) Import");

		addInputParameterLog(builder);
		log += builder.buildParameterList();

		//Output first part of log message...
		logService.info(log);

		ObjectMapper mapper = new ObjectMapper();
		
		try {
			JsonNode jsonNode = mapper.readTree(file);
			String datasetName = (jsonNode.has("desc")) ? jsonNode.get("desc").asText() : "Converted Single-molecule Dataset";
		  String datasetId = (jsonNode.has("id")) ? jsonNode.get("id").asText() : MarsMath.getUUID58().substring(0, 10);
		  String datasetAttr = (jsonNode.has("attr")) ? jsonNode.get("attr").asText() : "";
			
			archive = new SingleMoleculeArchive(datasetName);
			
			MarsOMEMetadata meta = archive.createMetadata(datasetId);
			meta.setNotes(datasetAttr);
			archive.putMetadata(meta);
			
			JsonNode data = jsonNode.get("data");
			for (JsonNode node : data) {
				String record_id = node.get("id").asText();
				
				MarsTable table = new MarsTable(record_id);
				
				JsonNode index = node.get("index");
				DoubleColumn iCol = new DoubleColumn("index");
				for (JsonNode indexNode : index)
					iCol.add(indexNode.doubleValue());
					
				table.add(iCol);
				
				JsonNode values = node.get("values");
				values.fieldNames().forEachRemaining(field -> {
					DoubleColumn col = new DoubleColumn(field);
					JsonNode array = values.get(field);
					for (JsonNode value : array)
						col.add(value.doubleValue());
					table.add(col);
				});
				
				SingleMolecule molecule = archive.createMolecule(record_id, table);
				molecule.setMetadataUID(datasetId);
				if (node.has("attr")) molecule.setNotes(node.get("attr").asText());
				archive.put(molecule);
			}
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			logService.info(LogBuilder.endBlock(false));
		} catch (IOException e) {
			e.printStackTrace();
			logService.info(LogBuilder.endBlock(false));
		}
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	  logService.info(LogBuilder.endBlock(true));
	  archive.logln(log);
	  archive.logln(LogBuilder.endBlock(true));
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("File", file.getAbsolutePath());
	}
}
