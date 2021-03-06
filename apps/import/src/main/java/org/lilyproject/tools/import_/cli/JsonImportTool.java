/*
 * Copyright 2010 Outerthought bvba
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lilyproject.tools.import_.cli;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Splitter;
import com.ngdata.lily.security.hbase.client.AuthorizationContext;
import org.lilyproject.repository.spi.AuthorizationContextHolder;
import org.lilyproject.tools.import_.json.IgnoreAndDeleteEmptyFieldsRecordReader;
import org.lilyproject.tools.import_.json.IgnoreEmptyFieldsRecordReader;
import org.lilyproject.tools.import_.json.RecordReader;
import org.lilyproject.util.hbase.RepoAndTableUtil;


import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.lilyproject.cli.BaseZkCliTool;
import org.lilyproject.cli.OptionUtil;
import org.lilyproject.client.LilyClient;
import org.lilyproject.repository.api.LRepository;
import org.lilyproject.repository.api.LTable;
import org.lilyproject.util.Version;
import org.lilyproject.util.hbase.LilyHBaseSchema.Table;
import org.lilyproject.util.io.Closer;

public class JsonImportTool extends BaseZkCliTool {
    private Option schemaOnlyOption;
    private Option workersOption;
    private Option quietOption;
    private Option tableOption;
    private Option repositoryOption;
    private Option fileFormatOption;
    private Option ignoreEmptyFieldsOption;
    private Option ignoreAndDeleteEmptyFieldsOption;
    private Option maxErrorsOption;
    private Option rolesOption;
    private LilyClient lilyClient;

    @Override
    protected String getCmdName() {
        return "lily-import";
    }

    @Override
    protected String getVersion() {
        return Version.readVersion("org.lilyproject", "lily-import");
    }

    public static void main(String[] args) throws Exception {
        new JsonImportTool().start(args);
    }

    @Override
    @SuppressWarnings("static-access")
    public List<Option> getOptions() {
        List<Option> options = super.getOptions();

        workersOption = OptionBuilder
                .withArgName("count")
                .hasArg()
                .withDescription("Number of workers (threads)")
                .withLongOpt("workers")
                .create("w");
        options.add(workersOption);

        schemaOnlyOption = OptionBuilder
                .withDescription("Only import the field types and record types, not the records.")
                .withLongOpt("schema-only")
                .create("s");
        options.add(schemaOnlyOption);

        quietOption = OptionBuilder
                .withDescription("Instead of printing out all record ids, only print a dot every 1000 records")
                .withLongOpt("quiet")
                .create("q");
        options.add(quietOption);

        tableOption = OptionBuilder
                .withArgName("table")
                .hasArg()
                .withDescription("Repository table to import to, defaults to record table")
                .withLongOpt("table")
                .create();
        options.add(tableOption);

        repositoryOption = OptionBuilder
                .withArgName("repository")
                .hasArg()
                .withDescription("Repository name, if not specified default repository is used")
                .withLongOpt("repository")
                .create();
        options.add(repositoryOption);

        fileFormatOption = OptionBuilder
                .withArgName("format")
                .hasArg()
                .withDescription("Input file format (see explanation at bottom)")
                .withLongOpt("format")
                .create();
        options.add(fileFormatOption);

        ignoreEmptyFieldsOption = OptionBuilder
                .withDescription("Ignores fields defined as empty strings, ignores zero-length lists, ignores nested" +
                        " records containing no fields. When in root record, adds them as fields-to-delete.")
                .withLongOpt("ignore-empty-fields")
                .create();
        options.add(ignoreEmptyFieldsOption);

        ignoreAndDeleteEmptyFieldsOption = OptionBuilder
                .withDescription("Does everything ignore-empty-fields does, and adds empty fields in the root record" +
                        "to the list of fields-to-delete (only makes sense for updates).")
                .withLongOpt("ignore-and-delete-empty-fields")
                .create();
        options.add(ignoreAndDeleteEmptyFieldsOption);

        maxErrorsOption = OptionBuilder
                .withArgName("count")
                .hasArg()
                .withDescription("Give up the import after this amount of errors (only for records, not schema)")
                .withLongOpt("max-errors")
                .create();
        options.add(maxErrorsOption);

        rolesOption = OptionBuilder
                .withArgName("roles")
                .hasArg()
                .withDescription("Comma-separated list of active user roles (excluding tenant part). Only has "
                        + "effect when the NGDATA hbase-authz coprocessor is installed.")
                .withLongOpt("roles")
                .create();
        options.add(rolesOption);

        return options;
    }

    @Override
    public int run(CommandLine cmd) throws Exception {
        int result = super.run(cmd);
        if (result != 0) {
            return result;
        }

        int workers = OptionUtil.getIntOption(cmd, workersOption, 1);

        String tableName = OptionUtil.getStringOption(cmd, tableOption, Table.RECORD.name);
        String repositoryName = OptionUtil.getStringOption(cmd, repositoryOption, RepoAndTableUtil.DEFAULT_REPOSITORY);
        ImportFileFormat fileFormat = OptionUtil.getEnum(cmd, fileFormatOption, ImportFileFormat.JSON, ImportFileFormat.class);

        if (cmd.getArgList().size() < 1) {
            System.out.println("No import file specified!");
            return 1;
        }

        boolean schemaOnly = cmd.hasOption(schemaOnlyOption.getOpt());
        boolean ignoreEmptyFields = cmd.hasOption(ignoreEmptyFieldsOption.getLongOpt());
        boolean ignoreAndDeleteEmptyFields = cmd.hasOption(ignoreAndDeleteEmptyFieldsOption.getLongOpt());
        long maxErrors = OptionUtil.getLongOption(cmd, maxErrorsOption, 1L);

        if (cmd.hasOption(rolesOption.getLongOpt())) {
            Set<String> roles = new HashSet<String>();
            Splitter splitter = Splitter.on(",").trimResults().omitEmptyStrings();
            for (String role : splitter.split(cmd.getOptionValue(rolesOption.getLongOpt()))) {
                roles.add(role);
            }
            AuthorizationContextHolder.setCurrentContext(new AuthorizationContext("lily-import", repositoryName, roles));
        }

        lilyClient = new LilyClient(zkConnectionString, zkSessionTimeout);

        for (String arg : (List<String>)cmd.getArgList()) {
            System.out.println("----------------------------------------------------------------------");
            System.out.println("Importing " + arg + " to " + tableName + " table of repository " + repositoryName);
            InputStream is = new FileInputStream(arg);
            try {
                LRepository repository = lilyClient.getRepository(repositoryName);
                LTable table = repository.getTable(tableName);
                ImportListener importListener;
                if (cmd.hasOption(quietOption.getOpt())) {
                    importListener = new DefaultImportListener(System.out, EntityType.RECORD);
                } else {
                    importListener = new DefaultImportListener();
                }

                JsonImport.ImportSettings settings = new JsonImport.ImportSettings();
                settings.importListener = importListener;
                settings.threadCount = workers;
                settings.maximumRecordErrors = maxErrors;
                if (ignoreAndDeleteEmptyFields) {
                    settings.recordReader= IgnoreAndDeleteEmptyFieldsRecordReader.INSTANCE;
                } else if (ignoreEmptyFields) {
                    settings.recordReader = IgnoreEmptyFieldsRecordReader.INSTANCE;
                } else {
                    settings.recordReader = RecordReader.INSTANCE;
                }

                switch (fileFormat) {
                    case JSON:
                        if (schemaOnly) {
                            JsonImport.loadSchema(repository, is, settings);
                        } else {
                            JsonImport.load(table, repository, is, settings);
                        }
                        break;
                    case JSON_LINES:
                        JsonImport.loadJsonLines(table, repository, is, settings);
                        break;
                    default:
                        throw new RuntimeException("Unexpected import file format: " + fileFormat);
                }
            } finally {
                Closer.close(is);
            }
        }

        System.out.println("Import done");

        return 0;
    }

    @Override
    protected void cleanup() {
        Closer.close(lilyClient);
        super.cleanup();
    }

    public enum ImportFileFormat {
        JSON, JSON_LINES
    }
}
