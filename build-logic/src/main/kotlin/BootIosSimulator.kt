import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Boots an iOS simulator so a Kotlin/Native test binary can reach the GPU.
 *
 * Kotlin's simulator test task runs binaries with `simctl spawn --standalone`, which
 * bypasses the simulator's launchd. That is faster and needs no booted device, but the
 * process then has no Metal device at all — Filament aborts with "Could not obtain Metal
 * device". Any module whose tests touch the GPU has to give up standalone mode and boot a
 * device first; this task is the second half of that.
 *
 * Booting is idempotent, so the task is cheap on a machine that already has a simulator up.
 */
abstract class BootIosSimulator : DefaultTask() {

    @get:Input
    abstract val device: Property<String>

    @get:Inject
    abstract val exec: ExecOperations

    @TaskAction
    fun boot() {
        val name = device.get()
        exec.exec {
            commandLine("xcrun", "simctl", "boot", name)
            // Already-booted is the common case and is reported as a failure.
            isIgnoreExitValue = true
        }
        exec.exec { commandLine("xcrun", "simctl", "bootstatus", name, "-b") }
    }
}
