package com.example.suraksha.data

data class Quote(
    val text: String,
    val author: String
)

object QuotesManager {
    private val quotes = listOf(
        Quote("Be strong, be fearless, be beautiful. And believe that anything is possible when you have the right people there to support you.", "Misty Copeland"),
        Quote("The most difficult thing is the decision to act, the rest is merely tenacity.", "Amelia Earhart"),
        Quote("I am not afraid of storms, for I am learning how to sail my ship.", "Louisa May Alcott"),
        Quote("Courage is the most important of all the virtues because without courage, you can't practice any other virtue consistently.", "Maya Angelou"),
        Quote("The future belongs to those who believe in the beauty of their dreams.", "Eleanor Roosevelt"),
        Quote("You are never too old to set another goal or to dream a new dream.", "C.S. Lewis"),
        Quote("Strength does not come from the physical capacity. It comes from an indomitable will.", "Mahatma Gandhi"),
        Quote("The only way to do great work is to love what you do.", "Steve Jobs"),
        Quote("Believe you can and you're halfway there.", "Theodore Roosevelt"),
        Quote("Success is not final, failure is not fatal: it is the courage to continue that counts.", "Winston Churchill"),
        Quote("The best way to predict the future is to create it.", "Peter Drucker"),
        Quote("Your time is limited, don't waste it living someone else's life.", "Steve Jobs"),
        Quote("The only limit to our realization of tomorrow will be our doubts of today.", "Franklin D. Roosevelt"),
        Quote("It always seems impossible until it's done.", "Nelson Mandela"),
        Quote("The journey of a thousand miles begins with one step.", "Lao Tzu"),
        Quote("What you get by achieving your goals is not as important as what you become by achieving your goals.", "Zig Ziglar"),
        Quote("The mind is everything. What you think you become.", "Buddha"),
        Quote("Life is 10% what happens to you and 90% how you react to it.", "Charles R. Swindoll"),
        Quote("The only person you are destined to become is the person you decide to be.", "Ralph Waldo Emerson"),
        Quote("Don't watch the clock; do what it does. Keep going.", "Sam Levenson"),
        Quote("The way to get started is to quit talking and begin doing.", "Walt Disney"),
        Quote("Success usually comes to those who are too busy to be looking for it.", "Henry David Thoreau"),
        Quote("The only place where success comes before work is in the dictionary.", "Vidal Sassoon"),
        Quote("Don't be afraid to give up the good to go for the great.", "John D. Rockefeller"),
        Quote("I find that the harder I work, the more luck I seem to have.", "Thomas Jefferson"),
        Quote("The future depends on what you do today.", "Mahatma Gandhi"),
        Quote("It does not matter how slowly you go as long as you do not stop.", "Confucius"),
        Quote("The only impossible journey is the one you never begin.", "Tony Robbins"),
        Quote("What lies behind us and what lies before us are tiny matters compared to what lies within us.", "Ralph Waldo Emerson"),
        Quote("The greatest glory in living lies not in never falling, but in rising every time we fall.", "Nelson Mandela"),
        Quote("The power of imagination makes us infinite.", "John Muir"),
        Quote("Life is really simple, but we insist on making it complicated.", "Confucius"),
        Quote("The only true wisdom is in knowing you know nothing.", "Socrates"),
        Quote("Happiness is not something ready made. It comes from your own actions.", "Dalai Lama"),
        Quote("Peace comes from within. Do not seek it without.", "Buddha"),
        Quote("The mind is everything. What you think you become.", "Buddha"),
        Quote("Be the change that you wish to see in the world.", "Mahatma Gandhi"),
        Quote("In the middle of difficulty lies opportunity.", "Albert Einstein"),
        Quote("The only way to do great work is to love what you do.", "Steve Jobs"),
        Quote("Success is walking from failure to failure with no loss of enthusiasm.", "Winston Churchill")
    )

    fun getDailyQuote(): Quote {
        val today = java.time.LocalDate.now()
        val dayOfYear = today.dayOfYear
        val year = today.year
        
        // Use day of year and year to get consistent daily quote
        val index = (dayOfYear + year) % quotes.size
        return quotes[index]
    }

    fun getRandomQuote(): Quote {
        return quotes.random()
    }
}
