/*
 * MIT License
 *
 * Copyright (c) 2017 Connor Hartley
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.connorhartley.guardian.sequence;

import io.github.connorhartley.guardian.detection.check.CheckProvider;
import org.spongepowered.api.entity.living.player.User;

/**
 * Sequence Blueprint
 *
 * Represents an abstract way to create a {@link Sequence}
 * from a {@link SequenceBuilder}.
 */
public abstract class SequenceBlueprint {

    protected final CheckProvider provider;

    protected SequenceBlueprint(CheckProvider checkProvider) {
        this.provider = checkProvider;
    }

    /**
     * Create
     *
     * <p>An abstract method to create a {@link Sequence}.</p>
     *
     * @param user The {@link User} to create the sequence for
     * @return The {@link Sequence} for the {@link User}.
     */
    public abstract Sequence create(User user);

    /**
     * Get Check Provider
     *
     * <p>Returns the {@link CheckProvider} providing this {@link SequenceBlueprint}.</p>
     *
     * @return The {@link CheckProvider}
     */
    public CheckProvider getCheckProvider() {
        return this.provider;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof Sequence) {
            return ((Sequence) object).getProvider().equals(this.provider);
        } else if (object instanceof SequenceBlueprint) {
            return ((SequenceBlueprint) object).getCheckProvider().equals(this.provider);
        }

        return false;
    }

}
