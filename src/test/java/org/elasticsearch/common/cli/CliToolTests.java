/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.cli;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.cli.CommandLine;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.elasticsearch.common.cli.CliToolConfig.Builder.cmd;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 *
 */
public class CliToolTests extends CliToolTestCase {

    @Test
    public void testOK() throws Exception {
        Terminal terminal = new TerminalMock();
        final AtomicReference<Boolean> executed = new AtomicReference<>(false);
        final NamedCommand cmd = new NamedCommand("cmd", terminal) {
            @Override
            public CliTool.ExitStatus execute(Settings settings, Environment env) {
                executed.set(true);
                return CliTool.ExitStatus.OK;
            }
        };
        SingleCmdTool tool = new SingleCmdTool("tool", terminal, cmd);
        int status = tool.execute();
        assertExecuted(status, CliTool.ExitStatus.OK, executed, true);
    }

    @Test
    public void testUsageError() throws Exception {
        Terminal terminal = new TerminalMock();
        final AtomicReference<Boolean> executed = new AtomicReference<>(false);
        final NamedCommand cmd = new NamedCommand("cmd", terminal) {
            @Override
            public CliTool.ExitStatus execute(Settings settings, Environment env) {
                executed.set(true);
                return CliTool.ExitStatus.USAGE;
            }
        };
        SingleCmdTool tool = new SingleCmdTool("tool", terminal, cmd);
        int status = tool.execute();
        assertExecuted(status, CliTool.ExitStatus.USAGE, executed, true);
    }

    @Test
    public void testIOError() throws Exception {
        Terminal terminal = new TerminalMock();
        final AtomicReference<Boolean> executed = new AtomicReference<>(false);
        final NamedCommand cmd = new NamedCommand("cmd", terminal) {
            @Override
            public CliTool.ExitStatus execute(Settings settings, Environment env) throws Exception {
                executed.set(true);
                throw new IOException("io error");
            }
        };
        SingleCmdTool tool = new SingleCmdTool("tool", terminal, cmd);
        int status = tool.execute();
        assertExecuted(status, CliTool.ExitStatus.IO_ERROR, executed, true);
    }

    @Test
    public void testCodeError() throws Exception {
        Terminal terminal = new TerminalMock();
        final AtomicReference<Boolean> executed = new AtomicReference<>(false);
        final NamedCommand cmd = new NamedCommand("cmd", terminal) {
            @Override
            public CliTool.ExitStatus execute(Settings settings, Environment env) throws Exception {
                executed.set(true);
                throw new Exception("random error");
            }
        };
        SingleCmdTool tool = new SingleCmdTool("tool", terminal, cmd);
        int status = tool.execute();
        assertExecuted(status, CliTool.ExitStatus.CODE_ERROR, executed, true);
    }

    @Test
    public void testMultiCommand() {
        Terminal terminal = new TerminalMock();
        int count = randomIntBetween(2, 7);
        final AtomicReference<Boolean>[] executed = new AtomicReference[count];
        for (int i = 0; i < executed.length; i++) {
            executed[i] = new AtomicReference<>(false);
        }
        NamedCommand[] cmds = new NamedCommand[count];
        for (int i = 0; i < count; i++) {
            final int index = i;
            cmds[i] = new NamedCommand("cmd" + index, terminal) {
                @Override
                public CliTool.ExitStatus execute(Settings settings, Environment env) throws Exception {
                    executed[index].set(true);
                    return CliTool.ExitStatus.OK;
                }
            };
        }
        MultiCmdTool tool = new MultiCmdTool("tool", terminal, cmds);
        int cmdIndex = randomIntBetween(0, count-1);
        int status = tool.execute("cmd" + cmdIndex);
        assertThat(status, is(CliTool.ExitStatus.OK.status()));
        for (int i = 0; i < executed.length; i++) {
            assertThat(executed[i].get(), is(i == cmdIndex));
        }
    }

    @Test
    public void testMultiCommand_UnknownCommand() {
        Terminal terminal = new TerminalMock();
        int count = randomIntBetween(2, 7);
        final AtomicReference<Boolean>[] executed = new AtomicReference[count];
        for (int i = 0; i < executed.length; i++) {
            executed[i] = new AtomicReference<>(false);
        }
        NamedCommand[] cmds = new NamedCommand[count];
        for (int i = 0; i < count; i++) {
            final int index = i;
            cmds[i] = new NamedCommand("cmd" + index, terminal) {
                @Override
                public CliTool.ExitStatus execute(Settings settings, Environment env) throws Exception {
                    executed[index].set(true);
                    return CliTool.ExitStatus.OK;
                }
            };
        }
        MultiCmdTool tool = new MultiCmdTool("tool", terminal, cmds);
        int status = tool.execute("cmd" + count); // "cmd" + count doesn't exist
        assertThat(status, is(CliTool.ExitStatus.USAGE.status()));
        for (int i = 0; i < executed.length; i++) {
            assertThat(executed[i].get(), is(false));
        }
    }

    @Test
    public void testSingleCommand_ToolHelp() throws Exception {
        final AtomicInteger writeCounter = new AtomicInteger(0);
        Terminal terminal = new TerminalMock() {
            @Override
            public void doPrint(String msg, Object... args) {
                int count = writeCounter.incrementAndGet();
                switch (count) {
                    case 1:
                        assertThat(msg, equalTo("\n"));
                        break;
                    case 2:
                        assertThat(msg, equalTo("cmd1 help\n"));
                        break;
                    case 3:
                        assertThat(msg, equalTo("\n"));
                        break;
                    default:
                        fail("written more than expected");
                }
            }
        };
        final AtomicReference<Boolean> executed = new AtomicReference<>(false);
        final NamedCommand cmd = new NamedCommand("cmd1", terminal) {
            @Override
            public CliTool.ExitStatus execute(Settings settings, Environment env) throws Exception {
                executed.set(true);
                throw new IOException("io error");
            }
        };
        SingleCmdTool tool = new SingleCmdTool("tool", terminal, cmd);
        int status = tool.execute(args("-h"));
        assertExecuted(status, CliTool.ExitStatus.OK, writeCounter, 3);
    }

    @Test
    public void testMultiCommand_ToolHelp() {
        final AtomicInteger writeCounter = new AtomicInteger(0);
        Terminal terminal = new TerminalMock() {
            @Override
            public void doPrint(String msg, Object... args) {
                int count = writeCounter.incrementAndGet();
                switch (count) {
                    case 1:
                        assertThat(msg, equalTo("\n"));
                        break;
                    case 2:
                        assertThat(msg, equalTo("tool help\n"));
                        break;
                    case 3:
                        assertThat(msg, equalTo("\n"));
                        break;
                    default:
                        fail("written more than expected");
                }
            }
        };
        NamedCommand[] cmds = new NamedCommand[2];
        cmds[0] = new NamedCommand("cmd0", terminal) {
            @Override
            public CliTool.ExitStatus execute(Settings settings, Environment env) throws Exception {
                return CliTool.ExitStatus.OK;
            }
        };
        cmds[1] = new NamedCommand("cmd1", terminal) {
            @Override
            public CliTool.ExitStatus execute(Settings settings, Environment env) throws Exception {
                return CliTool.ExitStatus.OK;
            }
        };
        MultiCmdTool tool = new MultiCmdTool("tool", terminal, cmds);
        int status = tool.execute(args("-h"));
        assertExecuted(status, CliTool.ExitStatus.OK, writeCounter, 3);
    }

    @Test
    public void testMultiCommand_CmdHelp() {
        final AtomicInteger writeCounter = new AtomicInteger(0);
        Terminal terminal = new TerminalMock() {
            @Override
            public void doPrint(String msg, Object... args) {
                int count = writeCounter.incrementAndGet();
                switch (count) {
                    case 1:
                        assertThat(msg, equalTo("\n"));
                        break;
                    case 2:
                        assertThat(msg, equalTo("cmd1 help\n"));
                        break;
                    case 3:
                        assertThat(msg, equalTo("\n"));
                        break;
                    default:
                        fail("written more than expected");
                }
            }
        };
        NamedCommand[] cmds = new NamedCommand[2];
        cmds[0] = new NamedCommand("cmd0", terminal) {
            @Override
            public CliTool.ExitStatus execute(Settings settings, Environment env) throws Exception {
                return CliTool.ExitStatus.OK;
            }
        };
        cmds[1] = new NamedCommand("cmd1", terminal) {
            @Override
            public CliTool.ExitStatus execute(Settings settings, Environment env) throws Exception {
                return CliTool.ExitStatus.OK;
            }
        };
        MultiCmdTool tool = new MultiCmdTool("tool", terminal, cmds);
        int status = tool.execute(args("cmd1 -h"));
        assertExecuted(status, CliTool.ExitStatus.OK, writeCounter, 3);
    }

    private static void assertExecuted(int actualStatus, CliTool.ExitStatus expectedStatus, AtomicReference<Boolean> actualExecuted, boolean expectedExecuted) {
        assertThat(actualExecuted.get(), is(expectedExecuted));
        assertThat(actualStatus, is(expectedStatus.status()));
    }

    private static void assertExecuted(int actualStatus, CliTool.ExitStatus expectedStatus, AtomicInteger actualExecuted, int expectedExecuted) {
        assertThat(actualExecuted.get(), is(expectedExecuted));
        assertThat(actualStatus, is(expectedStatus.status()));
    }

    private static class SingleCmdTool extends CliTool {

        private final Command command;

        private SingleCmdTool(String name, Terminal terminal, NamedCommand command) {
            super(CliToolConfig.config(name, SingleCmdTool.class)
                    .cmds(cmd(command.name, command.getClass()))
                    .build(), terminal);
            this.command = command;
        }

        @Override
        protected Command parse(String cmdName, CommandLine cli) throws Exception {
            return command;
        }
    }

    private static class MultiCmdTool extends CliTool {

        private final Map<String, Command> commands;

        private MultiCmdTool(String name, Terminal terminal, NamedCommand... commands) {
            super(CliToolConfig.config(name, MultiCmdTool.class)
                    .cmds(cmds(commands))
                    .build(), terminal);
            ImmutableMap.Builder<String, Command> commandByName = ImmutableMap.builder();
            for (int i = 0; i < commands.length; i++) {
                commandByName.put(commands[i].name, commands[i]);
            }
            this.commands = commandByName.build();
        }

        @Override
        protected Command parse(String cmdName, CommandLine cli) throws Exception {
            return commands.get(cmdName);
        }

        private static CliToolConfig.Cmd[] cmds(NamedCommand... commands) {
            CliToolConfig.Cmd[] cmds = new CliToolConfig.Cmd[commands.length];
            for (int i = 0; i < commands.length; i++) {
                cmds[i] = cmd(commands[i].name, commands[i].getClass()).build();
            }
            return cmds;
        }
    }

    private static abstract class NamedCommand extends CliTool.Command {

        private final String name;

        private NamedCommand(String name, Terminal terminal) {
            super(terminal);
            this.name = name;
        }
    }


}