import { createStore } from '@tobilu/qmd'

/** @import { UpdateProgress, EmbedProgress } from '@tobilu/qmd' */

const store = await createStore({
  dbPath: './qmd.sqlite',
  configPath: './qmd.yml',
})

// Global context
await store.setGlobalContext(
  'Android TV screensaver app (Kotlin, Android 5+). Two modes: Photo Slideshow and Music Visualizer with 13 OpenGL ES 2.0 effects. Targets Android TV devices (Huawei TV Stick, Fire TV Stick). Entry: ScreensaverService (DreamService) → ScreensaverEngine.',
)

// source collection
await store.addContext('source', '/', 'Core classes: ScreensaverService, ScreensaverEngine, SettingsActivity, Prefs (all SharedPreferences key constants — always use these, never raw strings), ImageCache, WeatherFetcher, HttpClients, UpdateChecker, UpdateInstaller, BootReceiver')
await store.addContext('source', '/auth', 'OAuth managers: GoogleAuthManager, OneDriveAuthManager, DropboxAuthManager')
await store.addContext('source', '/source', 'Image source implementations of ImageSource interface (getImageUrls): GoogleDriveSource, OneDriveSource, DropboxSource, ImmichSource, NextcloudSource, SynologySource, LocalStorageSource, DefaultImagesSource')
await store.addContext('source', '/visualizer', 'Audio visualizer pipeline: VisualizerView (GLSurfaceView) → AudioEngine (FFT analysis) + VisualizerRenderer (GL ES 2.0) → 13 mode classes. AudioData carries the audio snapshot per frame.')
await store.addContext('source', '/visualizer/modes', '13 OpenGL ES 2.0 visualizer effect classes: YantraMode, CubeMode, TriFluxMode, LissajousMode, TunnelMode, CorridorMode, NovaMode, SpiralMode, BubblesMode, PlasmaMode, BranchesMode, BarsMode, WaterfallMode — all extend BaseMode')

// docs collection
await store.addContext('docs', '/', 'Reference docs: architecture.md (full class map), image-sources.md (auth patterns), visualizer-modes.md (effect specs), settings-reference.md (all Prefs keys + defaults), psysuals-port-notes.md (read before porting from psysuals)')

// root collection
await store.addContext('root', '/', 'Top-level project files: CLAUDE.md, CHANGELOG.md, README.md, visualizer-music-reactivity.md')

// Index + embed
await store.update({
  onProgress: /** @param {UpdateProgress} p */ ({ collection, file, current, total }) =>
    process.stdout.write(`\r[${collection}] ${current}/${total} ${file}`),
})
console.log()

await store.embed({
  chunkStrategy: 'auto',
  onProgress: /** @param {EmbedProgress} p */ ({ current, total, collection }) =>
    process.stdout.write(`\r[${collection}] embedding ${current}/${total}`),
})
console.log()

await store.close()
console.log('qmd index ready.')
