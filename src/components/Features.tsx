import { motion } from 'framer-motion'

const featureGroups = [
  {
    icon: '🎵',
    title: 'Playback',
    gradient: 'from-violet-500/20 via-violet-500/5 to-transparent',
    features: [
      'Gapless playback',
      'Crossfade between songs',
      'ReplayGain 2.0 (per-track & per-album)',
      'Skip silence',
      'Speed & pitch control',
      'Smart shuffle',
      'Sleep timer',
    ],
  },
  {
    icon: '📝',
    title: 'Lyrics',
    gradient: 'from-blue-500/20 via-blue-500/5 to-transparent',
    features: [
      'Synced lyrics (LRC, TTML, karaoke)',
      'Embedded ID3 lyrics',
      'LRCLib auto-fetch',
      'In-app lyrics editor',
    ],
  },
  {
    icon: '📚',
    title: 'Library',
    gradient: 'from-emerald-500/20 via-emerald-500/5 to-transparent',
    features: [
      'Full MediaStore integration',
      'Folder browsing',
      'Configurable artist delimiters',
      'Excluded folders',
      'Auto-scan & background sync',
      'Sort & filter by any field',
      'Persistent queue',
    ],
  },
  {
    icon: '📡',
    title: 'Streaming & Sharing',
    gradient: 'from-cyan-500/20 via-cyan-500/5 to-transparent',
    features: [
      'Navidrome / Subsonic support',
      'Chromecast / Google Cast',
      'Resonance Share (Wi-Fi P2P)',
    ],
  },
  {
    icon: '📊',
    title: 'Scrobbling & Stats',
    gradient: 'from-rose-500/20 via-rose-500/5 to-transparent',
    features: [
      'Last.fm scrobbling',
      'Maloja self-hosted scrobbling',
      'Listening history & play counts',
      'Auto-generated weekly mixes',
      'Play breakdowns by hour & period',
    ],
  },
  {
    icon: '🎨',
    title: 'UI & Customization',
    gradient: 'from-amber-500/20 via-amber-500/5 to-transparent',
    features: [
      'Material 3 with dynamic color',
      'Dark / Light / System theme',
      'Waveform seekbar',
      'Rotating vinyl animation',
      'Multiple player layouts',
      'Blur artwork background',
      'Home screen widget',
      'Tag editor',
    ],
  },
]

export default function Features() {
  return (
    <section id="features" className="py-28 px-6">
      <div className="max-w-6xl mx-auto">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5 }}
          className="text-center mb-16"
        >
          <h2 className="text-4xl md:text-5xl font-bold tracking-tight mb-4">Everything you need</h2>
          <p className="text-[#B0A8C8] text-lg max-w-lg mx-auto">
            Resonance covers every corner of the audiophile experience.
          </p>
        </motion.div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {featureGroups.map((group, i) => (
            <motion.div
              key={group.title}
              initial={{ opacity: 0, y: 32 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: '-40px' }}
              transition={{ duration: 0.5, delay: i * 0.08 }}
              whileHover={{ y: -4 }}
              className="relative group rounded-2xl border border-white/[0.07] hover:border-white/[0.13] bg-surface/60 backdrop-blur-sm p-6 overflow-hidden transition-colors duration-200"
            >
              {/* Card tint */}
              <div className={`absolute inset-0 bg-gradient-to-br ${group.gradient} opacity-60 pointer-events-none`} />
              {/* Hover glow edge */}
              <div className="absolute inset-0 rounded-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-300 shadow-[inset_0_1px_0_rgba(255,255,255,0.07)]" />

              <div className="relative">
                <div className="flex items-center gap-3 mb-5">
                  <div className="text-xl w-10 h-10 flex items-center justify-center bg-white/[0.06] rounded-xl flex-shrink-0">
                    {group.icon}
                  </div>
                  <h3 className="text-sm font-semibold text-[#EDE9F6]">{group.title}</h3>
                </div>
                <ul className="space-y-2.5">
                  {group.features.map(feature => (
                    <li key={feature} className="flex items-start gap-2.5 text-sm text-[#A89FC0]">
                      <span className="mt-[5px] w-1.5 h-1.5 rounded-full bg-primary/60 flex-shrink-0" />
                      {feature}
                    </li>
                  ))}
                </ul>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}
