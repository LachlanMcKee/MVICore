import org.gradle.api.Plugin
import org.gradle.api.Project

internal abstract class MviCorePublishPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply {
            apply("maven-publish")
        }

        configureDocAndSources(project)
    }

    protected abstract fun configureDocAndSources(project: Project)

    protected abstract fun getComponentName(): String
}
