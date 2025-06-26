// disable-crumbs.groovy
import jenkins.model.Jenkins

Jenkins.instance.setCrumbIssuer(null)
println "--> CSRF protection disabled via init script"