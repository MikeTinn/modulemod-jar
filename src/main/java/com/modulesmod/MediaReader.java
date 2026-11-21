package com.modulesmod;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Finds out which song is playing on this computer by asking Windows (the same media session that
 * the volume flyout uses) through a small PowerShell script that prints one line every ~400 ms.
 * Works with Spotify, browsers, most music players. Windows only.
 */
public final class MediaReader {
	/** What is playing right now. positionMs was measured when the line arrived (receivedAtMs). */
	public record Track(String title, String artist, String album, long durationMs, long positionMs,
						boolean playing, long receivedAtMs) {}

	private static final String SCRIPT = """
			$ErrorActionPreference = 'SilentlyContinue'
			[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
			Add-Type -AssemblyName System.Runtime.WindowsRuntime
			$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
			function Await($op, $type) {
			  $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
			  $t.Wait(-1) | Out-Null
			  $t.Result
			}
			[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
			[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
			$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
			while ($true) {
			  $line = 'NONE'
			  try {
			    $s = $mgr.GetCurrentSession()
			    if ($s -ne $null) {
			      $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
			      $tl = $s.GetTimelineProperties()
			      $pb = $s.GetPlaybackInfo()
			      $playing = 0
			      if ($pb.PlaybackStatus.ToString() -eq 'Playing') { $playing = 1 }
			      $pos = [long]$tl.Position.TotalMilliseconds
			      if ($playing -eq 1) { $pos += [long]([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalMilliseconds }
			      $dur = [long]($tl.EndTime - $tl.StartTime).TotalMilliseconds
			      if ($dur -gt 0 -and $pos -gt $dur) { $pos = $dur }
			      $line = "$playing`t$pos`t$dur`t$($p.Artist)`t$($p.Title)`t$($p.AlbumTitle)"
			    }
			  } catch { }
			  [Console]::Out.WriteLine($line)
			  [Console]::Out.Flush()
			  Start-Sleep -Milliseconds 400
			}
			""";

	private static volatile Track current;
	private static Process process;
	private static Thread reader;

	private MediaReader() {}

	public static boolean supported() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}

	public static Track get() {
		return current;
	}

	public static synchronized void start() {
		if (process != null && process.isAlive()) return;
		if (!supported()) {
			Diagnostics.lyrics = "song detection needs Windows";
			return;
		}
		try {
			Path script = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("modulesmod-media.ps1");
			Files.writeString(script, SCRIPT, StandardCharsets.UTF_8);
			ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
					"-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-File", script.toString());
			pb.redirectError(ProcessBuilder.Redirect.DISCARD);
			Process p = pb.start();
			process = p;
			Runtime.getRuntime().addShutdownHook(new Thread(p::destroyForcibly));
			reader = new Thread(() -> readLoop(p), "modulesmod-media");
			reader.setDaemon(true);
			reader.start();
		} catch (Exception e) {
			Diagnostics.error("media reader", e);
		}
	}

	public static synchronized void stop() {
		if (process != null) process.destroyForcibly();
		process = null;
		current = null;
	}

	private static void readLoop(Process p) {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = in.readLine()) != null) {
				current = parse(line);
			}
		} catch (Exception ignored) {
		}
		if (process == p) current = null;
	}

	private static Track parse(String line) {
		String[] f = line.split("\t", -1);
		if (f.length < 6) return null;
		String title = f[4].trim();
		if (title.isEmpty()) return null;
		try {
			return new Track(title, f[3].trim(), f[5].trim(), Long.parseLong(f[2].trim()),
					Long.parseLong(f[1].trim()), "1".equals(f[0].trim()), System.currentTimeMillis());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
