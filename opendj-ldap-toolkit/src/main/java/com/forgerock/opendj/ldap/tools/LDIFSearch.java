/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2012-2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.ToolVersionHandler.newSdkVersionHandler;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.cli.Utils.filterExitCode;
import static com.forgerock.opendj.cli.CommonArguments.*;

import static org.forgerock.util.Utils.closeSilently;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldif.LDIF;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.MultiChoiceArgument;
import com.forgerock.opendj.cli.StringArgument;

/** This utility can be used to perform search operations against data in an LDIF file. */
public final class LDIFSearch extends ConsoleApplication {
    /**
     * The main method for LDIFSearch tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        final int retCode = new LDIFSearch().run(args);
        System.exit(filterExitCode(retCode));
    }

    private LDIFSearch() {
        // Nothing to do.
    }

    private int run(final String[] args) {
        /* Create the command-line argument parser for use with this program. */
        final LocalizableMessage toolDescription = INFO_LDIFSEARCH_TOOL_DESCRIPTION.get();
        final ArgumentParser argParser = new ArgumentParser(
            LDIFSearch.class.getName(), toolDescription, false, true, 1, 0, "source [filter] [attributes ...]");
        argParser.setVersionHandler(newSdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_LDIFSEARCH.get());

        final BooleanArgument showUsage;
        final StringArgument outputFilename;
        final BooleanArgument typesOnly;
        final IntegerArgument timeLimit;
        final StringArgument filename;
        final StringArgument baseDN;
        final MultiChoiceArgument<SearchScope> searchScope;
        final IntegerArgument sizeLimit;
        try {
            outputFilename =
                    StringArgument.builder(OPTION_LONG_OUTPUT_LDIF_FILENAME)
                            .shortIdentifier(OPTION_SHORT_OUTPUT_LDIF_FILENAME)
                            .description(INFO_LDIFSEARCH_DESCRIPTION_OUTPUT_FILENAME.get(
                                    INFO_OUTPUT_LDIF_FILE_PLACEHOLDER.get()))
                            .defaultValue("stdout")
                            .valuePlaceholder(INFO_OUTPUT_LDIF_FILE_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            baseDN =
                    StringArgument.builder(OPTION_LONG_BASEDN)
                            .shortIdentifier(OPTION_SHORT_BASEDN)
                            .description(INFO_SEARCH_DESCRIPTION_BASEDN.get())
                            .required()
                            .valuePlaceholder(INFO_BASEDN_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);

            searchScope = searchScopeArgument();
            argParser.addArgument(searchScope);

            filename =
                    StringArgument.builder(OPTION_LONG_FILENAME)
                            .shortIdentifier(OPTION_SHORT_FILENAME)
                            .description(INFO_SEARCH_DESCRIPTION_FILENAME.get())
                            .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            typesOnly =
                    BooleanArgument.builder("typesOnly")
                            .shortIdentifier('A')
                            .description(INFO_DESCRIPTION_TYPES_ONLY.get())
                            .buildAndAddToParser(argParser);
            sizeLimit =
                    IntegerArgument.builder("sizeLimit")
                            .shortIdentifier('z')
                            .description(INFO_SEARCH_DESCRIPTION_SIZE_LIMIT.get())
                            .defaultValue(0)
                            .valuePlaceholder(INFO_SIZE_LIMIT_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            timeLimit =
                    IntegerArgument.builder("timeLimit")
                            .shortIdentifier('l')
                            .description(INFO_SEARCH_DESCRIPTION_TIME_LIMIT.get())
                            .defaultValue(0)
                            .valuePlaceholder(INFO_TIME_LIMIT_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);

            showUsage = showUsageArgument();
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());
        } catch (final ArgumentException ae) {
            final LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
            errPrintln(message);
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        // Parse the command-line arguments provided to this program.
        try {
            argParser.parseArguments(args);

            /* If we should just display usage or version information, then print it and exit. */
            if (argParser.usageOrVersionDisplayed()) {
                return ResultCode.SUCCESS.intValue();
            }
        } catch (final ArgumentException ae) {
            argParser.displayMessageAndUsageReference(getErrStream(), ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        final List<Filter> filters = new LinkedList<>();
        final List<String> attributes = new LinkedList<>();
        final List<String> trailingArguments = argParser.getTrailingArguments();
        if (trailingArguments.size() > 1) {
            final List<String> filterAndAttributeStrings =
                    trailingArguments.subList(1, trailingArguments.size());

            /* The list of trailing arguments should be structured as follow:
             - If a filter file is present, trailing arguments are
             considered as attributes
             - If filter file is not present, the first trailing argument is
             considered the filter, the other as attributes.*/
            if (!filename.isPresent()) {
                final String filterString = filterAndAttributeStrings.remove(0);
                try {
                    filters.add(Filter.valueOf(filterString));
                } catch (final LocalizedIllegalArgumentException e) {
                    errPrintln(e.getMessageObject());
                    return ResultCode.CLIENT_SIDE_FILTER_ERROR.intValue();
                }
            }
            // The rest are attributes
            attributes.addAll(filterAndAttributeStrings);
        }

        if (filename.isPresent()) {
            // Read the filter strings.
            try (BufferedReader in = new BufferedReader(new FileReader(filename.getValue()))) {
                String line = null;
                while ((line = in.readLine()) != null) {
                    if ("".equals(line.trim())) {
                        // ignore empty lines.
                        continue;
                    }
                    filters.add(Filter.valueOf(line));
                }
            } catch (final LocalizedIllegalArgumentException e) {
                errPrintln(e.getMessageObject());
                return ResultCode.CLIENT_SIDE_FILTER_ERROR.intValue();
            } catch (final IOException e) {
                errPrintln(LocalizableMessage.raw(e.toString()));
                return ResultCode.CLIENT_SIDE_FILTER_ERROR.intValue();
            }
        }

        if (filters.isEmpty()) {
            argParser.displayMessageAndUsageReference(getErrStream(), ERR_SEARCH_NO_FILTERS.get());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        final SearchRequest search;
        try {
            final SearchScope scope = searchScope.getTypedValue();
            search =
                    Requests.newSearchRequest(DN.valueOf(baseDN.getValue()), scope, filters.get(0),
                            attributes.toArray(new String[attributes.size()])).setTypesOnly(
                            typesOnly.isPresent()).setTimeLimit(timeLimit.getIntValue())
                            .setSizeLimit(sizeLimit.getIntValue());
        } catch (final ArgumentException | LocalizedIllegalArgumentException e) {
            errPrintln(e.getMessageObject());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        InputStream sourceInputStream = null;
        OutputStream outputStream = null;

        try {
            // First source file.
            if (!"-".equals(trailingArguments.get(0))) {
                try {
                    sourceInputStream = new FileInputStream(trailingArguments.get(0));
                } catch (final FileNotFoundException e) {
                    final LocalizableMessage message =
                            ERR_LDIF_FILE_CANNOT_OPEN_FOR_READ.get(trailingArguments.get(0), e
                                    .getLocalizedMessage());
                    errPrintln(message);
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            }

            // Output file.
            if (outputFilename.isPresent() && !"-".equals(outputFilename.getValue())) {
                try {
                    outputStream = new FileOutputStream(outputFilename.getValue());
                } catch (final FileNotFoundException e) {
                    final LocalizableMessage message =
                            ERR_LDIF_FILE_CANNOT_OPEN_FOR_WRITE.get(outputFilename.getValue(), e
                                    .getLocalizedMessage());
                    errPrintln(message);
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            }

            // Default to stdin/stdout for all streams if not specified.
            if (sourceInputStream == null) {
                // Command line parameter was "-".
                sourceInputStream = System.in;
            }

            if (outputStream == null) {
                outputStream = System.out;
            }

            // Perform the search.
            try (LDIFEntryReader sourceReader = new LDIFEntryReader(sourceInputStream);
                LDIFEntryWriter outputWriter = new LDIFEntryWriter(outputStream)) {
                LDIF.copyTo(LDIF.search(sourceReader, search), outputWriter);
            }
        } catch (final IOException e) {
            if (e instanceof LocalizableException) {
                errPrintln(ERR_LDIFSEARCH_FAILED.get(((LocalizableException) e).getMessageObject()));
            } else {
                errPrintln(ERR_LDIFSEARCH_FAILED.get(e.getLocalizedMessage()));
            }
            return ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue();
        } finally {
            closeSilently(sourceInputStream, outputStream);
        }

        return ResultCode.SUCCESS.intValue();
    }
}
