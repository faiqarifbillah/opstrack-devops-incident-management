# Linux operations and troubleshooting

Commands and sample output were checked on Ubuntu 26.04 under WSL 2, with OpsTrack running in Docker Compose.

Open Ubuntu with `wsl -d Ubuntu`, then go to the project. Windows drives appear under `/mnt`:

```bash
cd /mnt/c/Users/User/Documents/OPSTRACK/opstrack-devops-incident-management
```

Docker in Ubuntu talks to the same Docker Desktop engine as Windows, so Docker Desktop must be running.

## Navigation and files

| Command | What it does |
|---|---|
| `pwd` | Show the current folder |
| `ls -la` | List files, including hidden ones, with details |
| `cd folder`, `cd ..`, `cd ~` | Go into a folder, up one level, or home |
| `mkdir -p a/b` | Create folders, including parents |
| `cp a b`, `mv a b` | Copy, or move and rename |
| `rm file` | Delete a file. There is no recycle bin |
| `cat file`, `less file` | Print a file, or page through it (`q` quits) |
| `find . -name '*.log'` | Find files by name |
| `du -sh .` | Show the size of the current folder |

## File permissions

```text
-rwxr--r-- 1 user user 33 Nov  1 21:54 run.sh
 ^^^ owner   read, write, execute
    ^^^ group  read
       ^^^ others  read
```

As numbers, read is 4, write is 2 and execute is 1, added together for each of owner, group and others.

| Command | Result | Typical use |
|---|---|---|
| `chmod u+x run.sh` | Owner may execute | Scripts |
| `chmod 600 secret.env` | Only the owner may read or write | Secrets, `.env` |
| `chmod 400 key.pem` | Only the owner may read | SSH private keys |
| `sudo chown user file` | Change the owner | Files created by another user |
| `stat -c '%a %U %n' file` | Show mode and owner, such as `600 user secret.env` | Checking |

Running a script without execute permission gives `Permission denied`. Avoid `chmod 777`: it hides the real problem by letting everyone do everything.

## Processes and resources

| Command | What it shows |
|---|---|
| `ps aux --sort=-%mem \| head` | Processes using the most memory |
| `top` | Live CPU and memory use (`q` quits) |
| `kill <pid>` | Ask a process to stop. Use `kill -9 <pid>` only if it ignores that |
| `free -h` | Memory. Read the `available` column, not `free` |
| `df -h` | Free disk space per filesystem |
| `du -sh * \| sort -h` | Largest items in the current folder |
| `uptime` | Time since boot, and load average |
| `docker stats --no-stream` | CPU and memory per container |
| `docker system df` | Disk used by Docker |

A full disk causes confusing failures, especially for databases, so check `df -h` early.

## Ports and networks

| Command | What it shows |
|---|---|
| `ss -ltnp` | Listening TCP ports and, where permitted, their processes |
| `ss -ltnp \| grep 8080` | Whether anything listens on port 8080 |
| `lsof -i :8080` | Which process uses a port. Use `sudo` to see other users' processes |
| `ip -brief addr` | This machine's network interfaces |
| `curl -i http://localhost:8080/actuator/health` | Make a request and show status and headers |
| `curl -sS -m 5 URL` | Show errors, and give up after 5 seconds |
| `nc -zv host 5432` | Test whether a TCP port is reachable |

Sample output:

```text
State  Recv-Q Send-Q  Local Address:Port  Peer Address:Port Process
LISTEN 0      5           127.0.0.1:18099      0.0.0.0:*    users:(("python3",pid=1381,fd=3))
```

`127.0.0.1` accepts connections from this machine only. `0.0.0.0` accepts them from every network interface, which matters on a server.

`netstat` is older and was not installed on this Ubuntu. Use `ss`.

A `curl` exit code narrows down the problem. Run `echo $?` straight after it:

| Exit code | Meaning | Usually |
|---|---|---|
| 0 | A response arrived | Check the HTTP status too |
| 7 | Connection refused | Nothing is listening: stopped, crashed or wrong port |
| 28 | Timed out | Frozen, overloaded, or blocked by a firewall |

## Logs

| Command | What it does |
|---|---|
| `tail -n 50 file` | Last 50 lines |
| `tail -f file` | Follow new lines; `Ctrl+C` stops |
| `grep -n ERROR file` | Matching lines with line numbers |
| `grep -c ERROR file` | Count matching lines |
| `awk '{print $1}' file \| sort \| uniq -c \| sort -rn` | Count by first word, most common first |
| `docker compose logs --tail 50 app` | Last 50 lines from a service |
| `docker compose logs -f app` | Follow a service |
| `docker compose logs --since 10m app` | Only the last 10 minutes |
| `docker compose logs app \| grep -E 'ERROR\|Caused by'` | Only the useful lines |

Read Java stack traces from the bottom `Caused by` upward. The last cause is usually the real one.

## Environment variables

```bash
export LAB_VAR=abc        # available to this shell and programs it starts
printenv LAB_VAR          # show one variable
env | grep LAB_VAR        # search all variables
unset LAB_VAR             # remove it
GREETING=hi sh -c 'echo $GREETING'   # set for one command only
```

A child process inherits variables from its parent, never the reverse. Avoid running bare `env` where others can see the screen or in shared logs, because it can print secrets.

## Services

`systemctl` manages long-running services on systemd machines, including this Ubuntu and a typical Ubuntu EC2 server.

| Command | What it does |
|---|---|
| `systemctl status cron` | Is a service running, and its recent log lines |
| `systemctl --failed` | All failed services |
| `sudo systemctl restart cron` | Restart a service |
| `sudo systemctl enable --now cron` | Start now and at every boot |
| `journalctl -u cron -n 50` | A service's last 50 log lines. May need `sudo` |
| `journalctl -u cron -f` | Follow a service's logs |

Here Docker runs from Docker Desktop, so there is no `docker` service inside Ubuntu. On an EC2 server with Docker Engine installed, use `systemctl status docker`.

## Investigation method

Work from the outside in, changing one thing at a time, and read before you restart. A restart can clear the evidence.

1. Reproduce it: what exactly fails, and what does `curl` return?
2. Check the state: `docker compose ps`.
3. Read the logs: `docker compose logs --tail 100 <service>`.
4. Check resources: `df -h`, `free -h`, `docker stats --no-stream`.
5. Form one theory, test it, and then fix it.

## Scenario 1: application not responding

Start with `curl`, because its result tells you which branch to follow:

```bash
curl -sS -m 5 http://localhost:8080/actuator/health; echo "exit=$?"
docker compose ps -a
```

| Observation | Meaning | Next step |
|---|---|---|
| Exit 7, container `exited` | The application stopped | `docker compose logs --tail 100 app`, then `docker compose start app` |
| Exit 28, status shows `Paused`, CPU 0% | The process is frozen | `docker compose unpause app` |
| Exit 28, container running and CPU or memory high | Overloaded or stuck | `docker stats --no-stream`, then read the logs |
| HTTP 503 and `{"status":"DOWN"}` | The application is running, but a dependency failed | See scenario 4 |

Check from inside the container to separate an application problem from a network or port problem:

```bash
docker compose exec app wget -qO- http://127.0.0.1:8080/actuator/health
```

If this works but `curl` from outside fails, look at published ports with `docker compose ps`.

The exit code `143` means the container was stopped normally by `SIGTERM` (128 + 15). `137` means it was killed (128 + 9), which often points to running out of memory.

## Scenario 2: port conflict

```text
Bind for 127.0.0.1:8080 failed: port is already allocated
```

Find who holds the port:

```bash
docker ps --filter publish=8080 --format '{{.Names}} {{.Ports}}'
ss -ltnp | grep 8080
```

On Windows, also check `netstat -ano | findstr :8080`, because a Windows program can hold it too.

Then either stop that program, or set a different `APP_PORT` in `.env`.

**Important:** after a port conflict, the failed `app` container can stay broken even once the port is free. In testing, `docker compose up -d` alone restarted it repeatedly with no published port, failing with `Unable to determine Dialect without JDBC metadata`. Recreate the containers instead. This keeps your data:

```bash
docker compose down
docker compose up -d
```

## Scenario 3: failed container

A crash loop can look healthy at a glance, because `restart: unless-stopped` keeps starting it:

```text
app | running | Up Less than a second (health: starting)
```

The giveaway is an uptime that never grows and a rising restart count. `docker compose ps` shows the container name to use:

```bash
docker compose ps -a
docker inspect --format 'restarts={{.RestartCount}} exit={{.State.ExitCode}}' <container>
docker compose logs --tail 200 app | grep -E 'ERROR|Caused by|must be set'
```

Sample cause:

```text
OPSTRACK_API_KEY must be set to at least 32 characters
```

Fix the configuration, then recreate the container with `docker compose up -d app`. A plain `restart` does not pick up changed environment variables.

## Scenario 4: database connection problem

Test each layer in order, from inside the application container:

```bash
docker compose exec app sh -c 'getent hosts db || echo "name not found"'
docker compose exec app nc -zv -w 3 db 5432
docker compose exec db pg_isready -h 127.0.0.1 -U postgres
docker compose logs --tail 50 db
```

| Evidence | Meaning | Fix |
|---|---|---|
| `nc: bad address 'db'`, or `UnknownHostException: db` | The `db` container is stopped, or not on the same network | `docker compose ps`, then `docker compose start db` |
| The name resolves but the port is closed | PostgreSQL has not finished starting, or failed | `docker compose logs db` |
| `FATAL: password authentication failed for user "opstrack"` | Wrong password | Check `DB_PASSWORD`. See [DOCKER_GUIDE.md](DOCKER_GUIDE.md) if it changed after the volume was created |
| `Unable to determine Dialect without JDBC metadata` | The application could not connect at startup. This is a symptom, not the cause | Look further up the logs, then check the layers above |
| Health `503 DOWN`, then `UP` once the database returns | Working as designed | Connections recover without restarting the application |

While the database is down, a request can wait up to about 30 seconds and then return a 500. That is the connection pool's timeout.

## Practice labs

Run these from the project folder with the stack running (`docker compose up -d`). None of them deletes data, as long as you do not add `-v` to `docker compose down`. Before each lab, predict what you will see.

### Lab 1: stopped application

```bash
docker compose stop app
curl -sS -m 5 http://localhost:8080/actuator/health; echo "exit=$?"
docker compose ps -a
docker compose start app
```

Expect exit 7 and an `exited` status. The health check returns once the application has started, in about 10 seconds.

### Lab 2: frozen application

```bash
docker compose pause app
curl -sS -m 5 http://localhost:8080/actuator/health; echo "exit=$?"
docker compose ps
docker compose unpause app
```

Expect exit 28 and `Paused`. Compare this with Lab 1, and explain why the exit codes differ.

### Lab 3: port conflict

```bash
docker compose down
docker run -d --name conflict -p 127.0.0.1:8080:8000 postgres:17-alpine sleep 300
docker compose up -d
docker ps --filter publish=8080 --format '{{.Names}} {{.Ports}}'
docker rm -f conflict
docker compose down
docker compose up -d
```

Expect `port is already allocated` from the first `up`. The last two commands recover the stack.

### Lab 4: crash loop

This overrides the key for one command only:

```bash
OPSTRACK_API_KEY=change-me docker compose up -d app
sleep 15
docker compose ps -a
docker compose logs --tail 200 app | grep 'must be set'
docker compose up -d app
```

Expect a very short uptime and the `must be set` message. The last command returns to the key in `.env`.

### Lab 5: wrong database password

```bash
DB_PASSWORD=wrong-password docker compose up -d app
sleep 25
docker compose logs --tail 30 db | grep FATAL
docker compose up -d app
```

Expect `password authentication failed`.

### Lab 6: database stopped

```bash
docker compose stop db
docker compose exec app nc -zv -w 3 db 5432
curl -s -m 40 -w ' [%{http_code}]\n' http://localhost:8080/actuator/health
docker compose start db
sleep 12
curl -s http://localhost:8080/actuator/health
```

Expect `bad address 'db'` and `{"status":"DOWN"}` with `503`, then `UP` without restarting the application.

## Cleaning up disk

`docker system prune` removes stopped containers, unused networks, dangling images and build cache. It keeps volumes unless you add `--volumes`, which **deletes database data**. Removing the build cache makes your next build slow, so run `docker system df` first.
