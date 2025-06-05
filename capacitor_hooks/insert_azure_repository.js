const fs = require('fs');
const path = require('path');

const platform = process.env.CAPACITOR_PLATFORM_NAME;
console.log("\tCamera plugin - running hook after update - for " + platform);
const projectDirPath = process.env.CAPACITOR_ROOT_DIR;

if (platform == 'android') {
    fixAndroidAzureRepository();
}

/**
 * Add the azure repository (where camera native Android library is housed) to project root's build.gradle
 * Because capacitor plugins are injected as separate gradle modules, this is necessary for release builds lintVital gradle tasks to pass.
 */
function fixAndroidAzureRepository() {
    const gradleFilePath = path.resolve(projectDirPath, 'android/build.gradle');
    const azureUrl = 'https://pkgs.dev.azure.com/OutSystemsRD/9e79bc5b-69b2-4476-9ca5-d67594972a52/_packaging/PublicArtifactRepository/maven/v1';
    const mavenBlock = `        maven {
            url "${azureUrl}"
        }`;

    let gradleContent = fs.readFileSync(gradleFilePath, 'utf8');

    if (gradleContent.includes(azureUrl)) {
        console.log('\t[SKIPPED] Azure repository already in root build.gradle.');
    } else {
        const allprojectsStart = gradleContent.indexOf('allprojects {');
        if (allprojectsStart === -1) {
            console.warn('\t[WARNING] Could not find allprojects { ... } block. Unable to add Azure Repository');
            return;
        }
        const repositoriesStart = gradleContent.indexOf('repositories {', allprojectsStart);
        if (repositoriesStart === -1) {
            console.warn('\t[WARNING] Could not find allprojects { repositories { ... } } block. Unable to add Azure Repository');
            return;
        }
        // Track braces to find end of repositories block
        let braceCount = 0;
        let i = repositoriesStart + 'repositories {'.length - 1;
        let endIndex = -1;
        while (i < gradleContent.length) {
            if (gradleContent[i] === '{') braceCount++;
            else if (gradleContent[i] === '}') braceCount--;

            if (braceCount === 0) {
                endIndex = i;
                break;
            }
            i++;
        }
        if (endIndex === -1) {
            console.warn('\t[WARNING] Could not find allprojects { repositories { ... } } block. Unable to add Azure Repository');
            return;
        }
        const closingBraceLineStartIndex = gradleContent.lastIndexOf('\n', endIndex);
        // Insert the maven block at the end of the repositories block (before closing brace), because gradle searches repositories by order.
        // The Azure repo should be the last one since it will only apply for a few dependencies.
        // Otherwise this could slow down gradle build.
        const updatedContent = gradleContent.slice(0, closingBraceLineStartIndex) + '\n' + mavenBlock + gradleContent.slice(closingBraceLineStartIndex);
        fs.writeFileSync(gradleFilePath, updatedContent, 'utf8');
        console.log('\t[SUCCESS] Added Azure repository maven block to the root build.gradle.');
    }
}
