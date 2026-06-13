import { motion } from 'framer-motion'

const base = import.meta.env.BASE_URL

const screenshots = [
  { src: `${base}screenshots/home.png`, label: 'Home' },
  { src: `${base}screenshots/player.png`, label: 'Player' },
  { src: `${base}screenshots/lyrics.png`, label: 'Lyrics' },
  { src: `${base}screenshots/artist.png`, label: 'Artist' },
  { src: `${base}screenshots/playlist.png`, label: 'Playlist' },
  { src: `${base}screenshots/search.png`, label: 'Search' },
  { src: `${base}screenshots/share.png`, label: 'Share' },
  { src: `${base}screenshots/settings.png`, label: 'Settings' },
]

function PhoneFrame({ src, label, index }: { src: string; label: string; index: number }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 40 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: '-60px' }}
      transition={{ duration: 0.55, delay: index * 0.06, ease: 'easeOut' }}
      className="flex flex-col items-center gap-3"
    >
      <motion.div
        whileHover={{ y: -10, scale: 1.04 }}
        transition={{ duration: 0.28, ease: 'easeOut' }}
        className="relative w-full max-w-[160px]"
      >
        {/* Glow behind phone */}
        <div className="absolute -inset-2 bg-primary/10 rounded-[36px] blur-xl opacity-0 group-hover:opacity-100 transition-opacity" />

        {/* Phone frame */}
        <div className="relative rounded-[28px] border border-white/[0.12] bg-[#080810] overflow-hidden shadow-[0_24px_64px_rgba(0,0,0,0.7),inset_0_1px_0_rgba(255,255,255,0.06)]">
          {/* Notch bar */}
          <div className="h-7 bg-[#080810] flex items-center justify-center">
            <div className="w-14 h-[5px] bg-[#1a1825] rounded-full border border-white/[0.08]" />
          </div>
          {/* Screenshot */}
          <img src={src} alt={label} className="w-full block" loading="lazy" />
          {/* Home indicator */}
          <div className="h-5 bg-[#080810] flex items-center justify-center">
            <div className="w-16 h-[3px] bg-white/[0.15] rounded-full" />
          </div>
        </div>
      </motion.div>
      <span className="text-xs font-medium text-[#6B6380]">{label}</span>
    </motion.div>
  )
}

export default function Screenshots() {
  return (
    <section
      id="screenshots"
      className="py-28 px-6"
      style={{
        background:
          'linear-gradient(180deg, transparent 0%, rgba(19,18,31,0.5) 15%, rgba(19,18,31,0.5) 85%, transparent 100%)',
      }}
    >
      <div className="max-w-6xl mx-auto">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5 }}
          className="text-center mb-16"
        >
          <h2 className="text-4xl md:text-5xl font-bold tracking-tight mb-4">See it in action</h2>
          <p className="text-[#B0A8C8] text-lg max-w-lg mx-auto">
            Beautifully designed with Material 3 and full dynamic color support.
          </p>
        </motion.div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-6 justify-items-center">
          {screenshots.map((s, i) => (
            <PhoneFrame key={s.label} {...s} index={i} />
          ))}
        </div>
      </div>
    </section>
  )
}
