import type { Config } from 'tailwindcss'

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg: '#08080F',
        surface: '#13121F',
        s2: '#1A1829',
        s3: '#211F32',
        primary: '#A78BFA',
        'primary-light': '#C4B5FD',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      animation: {
        float: 'float 6s ease-in-out infinite',
        'pulse-glow': 'pulse-glow 3s ease-in-out infinite alternate',
      },
      keyframes: {
        float: {
          '0%, 100%': { transform: 'translateY(0px)' },
          '50%': { transform: 'translateY(-10px)' },
        },
        'pulse-glow': {
          '0%': { boxShadow: '0 0 40px rgba(167,139,250,0.25), 0 20px 60px rgba(0,0,0,0.5)' },
          '100%': { boxShadow: '0 0 80px rgba(167,139,250,0.55), 0 20px 60px rgba(0,0,0,0.5)' },
        },
      },
    },
  },
  plugins: [],
} satisfies Config
