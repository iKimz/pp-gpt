/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{vue,js,ts,jsx,tsx}'
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // Hiroshi Design System Palette
        primary: {
          DEFAULT: '#ffd700',
          50:  '#fffdf0',
          100: '#fffae0',
          200: '#fff3b3',
          300: '#ffeb80',
          400: '#ffe16d',
          500: '#ffd700',
          600: '#e9c400',
          700: '#705d00',
          800: '#544600',
          900: '#221b00',
        },
        // Brand alias pointing to Hiroshi Primary Yellow/Gold
        brand: {
          50:  '#fffdf0',
          100: '#fffae0',
          200: '#fff3b3',
          300: '#705d00',
          400: '#705d00',
          500: '#ffd700',
          600: '#ffd700',
          700: '#e9c400',
          800: '#705d00',
          900: '#544600',
          950: '#221b00',
        },
        // Hiroshi Surface Tier System
        surface: {
          DEFAULT: '#eeedf7',
          background: '#fbf8ff',
          lowest: '#ffffff',
          low: '#f4f2fd',
          high: '#e8e7f1',
          highest: '#e3e1ec',
          dim: '#dad9e3',
          dark: '#2f3038',
          on: '#1a1b22',
          muted: '#4d4732',
          outline: '#d0c6ab',
          // Mappings for legacy dark classes to light/hiroshi palette
          900: '#fbf8ff',
          800: '#ffffff',
          700: '#f4f2fd',
          600: '#eeedf7',
          500: '#e8e7f1',
          400: '#e3e1ec',
        },
        // Hiroshi Status & Accents
        hiroshi: {
          yellow: '#ffd700',
          primary: '#705d00',
          dark: '#1a1b22',
          muted: '#4d4732',
          container: '#eeedf7',
          border: '#e8e7f1',
          outline: '#d0c6ab',
        }
      },
      fontFamily: {
        heading: ['Plus Jakarta Sans', 'sans-serif'],
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
      borderRadius: {
        'sm': '0.25rem',
        'DEFAULT': '0.5rem',
        'md': '0.75rem',
        'lg': '1rem',
        'xl': '1.5rem',
        'full': '9999px',
      },
      boxShadow: {
        'glow': '0 0 20px rgba(99, 102, 241, 0.25)',
        'glow-lg': '0 0 40px rgba(99, 102, 241, 0.35)',
        'glass': '0 8px 32px rgba(0, 0, 0, 0.4)',
      },
      backdropBlur: {
        xs: '2px',
      },
      animation: {
        'fade-in': 'fadeIn 0.3s ease-out',
        'slide-up': 'slideUp 0.3s ease-out',
        'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'shimmer': 'shimmer 1.5s infinite',
        'typing': 'typing 1s step-end infinite',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        slideUp: {
          '0%': { transform: 'translateY(10px)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
        typing: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0' },
        }
      }
    }
  },
  plugins: [
    require('@tailwindcss/typography'),
  ]
}
