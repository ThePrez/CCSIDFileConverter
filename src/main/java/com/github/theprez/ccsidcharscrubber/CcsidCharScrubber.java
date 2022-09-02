package com.github.theprez.ccsidcharscrubber;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.LinkedList;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.ProcessLauncher;
import com.github.theprez.jcmdutils.StringUtils;

public class CcsidCharScrubber {

    enum ReplacementOpt {
        DELETE(CodingErrorAction.IGNORE), REPLACE(CodingErrorAction.REPLACE);
        private final CodingErrorAction m_codingErrorAction;

        private ReplacementOpt(final CodingErrorAction _codingErrorAction) {
            m_codingErrorAction = _codingErrorAction;
        }

        public CodingErrorAction getCodingErrorAction() {
            return m_codingErrorAction;
        }
    }

    private static boolean isIBMi() {
        return System.getProperty("os.name", "Misty").matches("(?i)OS/?400");
    }

    public static void main(final String[] _args) {

        final LinkedList<String> args = new LinkedList<String>();
        args.addAll(Arrays.asList(_args));

        final AppLogger logger = AppLogger.getSingleton(args.remove("-v"));

        String inputEncoding = "UTF-8";
        String outputEncoding = "UTF-8";
        ReplacementOpt opt = ReplacementOpt.DELETE;
        String replacement = "?";
        String outFile = null;
        String inFile = null;

        boolean isReplacingSmartQuotes = false;

        for (final String remainingArg : args) {
            if (remainingArg.toLowerCase().startsWith("--opt=")) {
                try {
                    opt = ReplacementOpt.valueOf(remainingArg.replaceFirst(".*=", "").toUpperCase());
                } catch (final Exception e) {
                    logger.println_err("ERROR: invalid option");
                }
            } else if (remainingArg.toLowerCase().startsWith("--in-ccsid=")) {
                try {
                    inputEncoding = CcsidUtils.unknownStringToEncoding(remainingArg.replaceFirst(".*=", ""), null);
                } catch (final Exception e) {
                    logger.println_err("ERROR: invalid input ccsid");
                }
            } else if (remainingArg.toLowerCase().startsWith("--out-ccsid=")) {
                try {
                    outputEncoding = CcsidUtils.unknownStringToEncoding(remainingArg.replaceFirst(".*=", ""), null);
                } catch (final Exception e) {
                    logger.println_err("ERROR: invalid output ccsid");
                }
            } else if (remainingArg.toLowerCase().startsWith("--in=")) {
                inFile = remainingArg.replaceFirst(".*=", "");
            } else if (remainingArg.toLowerCase().startsWith("--out=")) {
                outFile = remainingArg.replaceFirst(".*=", "");
            } else if (remainingArg.toLowerCase().startsWith("--replacement=")) {
                replacement = remainingArg.replaceFirst(".*=", "");
            } else if (remainingArg.equalsIgnoreCase("--smart-quotes")) {
                isReplacingSmartQuotes = true;
            } else if (remainingArg.equalsIgnoreCase("--no-smart-quotes")) {
                isReplacingSmartQuotes = false;
            } else if (remainingArg.equalsIgnoreCase("--help") || remainingArg.equalsIgnoreCase("-h")) {
                printUsageAndExit();
            } else {
                logger.println_warn("WARNING: Argument '" + remainingArg + "' unrecognized and will be ignored");
            }
        }
        if (StringUtils.isEmpty(inFile)) {
            logger.println_err("ERROR: No input file specified");
            printUsageAndExit();
        }
        if (StringUtils.isEmpty(outFile)) {
            logger.printfln_warn("WARNING: No output file specified. Defaulting to %s.out", inFile);
            outFile = inFile + ".out";
        }
        try {
            final CharsetEncoder encoder = Charset.forName(outputEncoding).newEncoder();
            encoder.replaceWith(replacement.getBytes(outputEncoding));
            encoder.onUnmappableCharacter(opt.getCodingErrorAction());
            encoder.onMalformedInput(opt.getCodingErrorAction());
            try (FileInputStream fis = new FileInputStream(inFile); InputStreamReader isr = new InputStreamReader(fis, inputEncoding); BufferedReader br = new BufferedReader(isr); FileOutputStream fos = new FileOutputStream(outFile); FileChannel fw = fos.getChannel()) {
                String line = null;
                while (null != (line = br.readLine())) {
                    System.out.println("line:" + line);
                    if (isReplacingSmartQuotes) {
                        line = line.replaceAll("[\\u2018\\u2019]", "'").replaceAll("[\\u201C\\u201D]", "\"");
                    }
                    final CharBuffer cbuf = CharBuffer.wrap(line);
                    final ByteBuffer encoded = encoder.encode(cbuf);
                    fw.write(encoded);
                }
            }
            setCcsidTag(logger, new File(outFile), CcsidUtils.encodingToCCSID(outputEncoding));
            logger.printf_success("Success");

        } catch (final Exception e) {
            logger.printExceptionStack_verbose(e);
            logger.println_err(e.getLocalizedMessage());
        }
    }

    private static void printUsageAndExit() {
        // @formatter:off
       final String usage = "Usage: java -jar ccsidconverter.jar  [options] <file>\n"
                                + "\n"
                                + "    Valid options include:\n"
                                + "        --in=<file>:             Input file.\n"
                                + "        --out=<file>:            Output file.\n"
                                + "        --opt=<replace/delete>:  How to handle unconvertible characters (default: delete)\n"
                                + "        --in-ccsid=<ccsid>:      Input file CCSID.\n"
                                + "        --out-ccsid=<ccsid>:     Output file CCSID.\n"
                                + "        --replacement=<char>:    Replacement character to use if replacing.\n"
                                + "        --smart-quotes:          Replace \"smart quotes\" with standard quotes.\n"
                                + "\n"
                                ;
        // @formatter:on
        System.err.println(usage);
        System.exit(-1);
    }

    private static void setCcsidTag(final AppLogger _logger, final File _file, final int _ccsid) throws IOException, InterruptedException {
        if (!isIBMi() || 0 > _ccsid) {
            _logger.println_verbose("Skipping setting of CCSID tag");
        }
        _logger.printfln_verbose("Trying to set CCSID of file '%s' to %d", _file.getName(), _ccsid);
        final Process p = Runtime.getRuntime().exec(new String[] { "/QOpenSys/usr/bin/setccsid", "" + _ccsid, _file.getAbsolutePath() });
        ProcessLauncher.pipeStreamsToCurrentProcess("SETCCSID", p, _logger);
        _logger.println_verbose("CCSID set rc=" + p.waitFor());
    }
}
